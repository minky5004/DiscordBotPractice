package com.minky.discordbot;

import com.minky.discordbot.NoticeStore.NoticeChannel;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

// 정기 업데이트 공지의 점검 시작 · 종료 시각에 공지 채널로 멘션한다
class MaintenanceAlert {

    private static final Logger log = LoggerFactory.getLogger(MaintenanceAlert.class);

    record Maintenance(String gid, Instant startsAt, Instant endsAt) {
    }

    static final ZoneId KST = ZoneId.of("Asia/Seoul");

    // Steam 의 이벤트 타입 · 시작 시각 필드는 주마다 어긋나지만, 이 제목 형식은 1년 동안 그대로다
    private static final Pattern TITLE = Pattern.compile("^(\\d{4})년 (\\d{1,2})월 (\\d{1,2})일 정기 업데이트 안내$");

    // 점검 시각은 공지 이미지 안에만 있다. "2026년 9월 17일 06:00 ~ 12:00 (KST)에 정기 점검이 …"
    private static final Pattern WINDOW = Pattern.compile("(\\d{1,2})월\\s*(\\d{1,2})일\\s*(\\d{1,2}):(\\d{2})\\s*~\\s*(\\d{1,2}):(\\d{2})");

    // 대형 업데이트가 아닌 주의 점검 시각
    private static final LocalTime USUAL_START = LocalTime.of(10, 0);
    private static final LocalTime USUAL_END = LocalTime.of(12, 0);

    private static final Duration OCR_TIMEOUT = Duration.ofSeconds(30);

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(task -> {
        // 공지 폴러와 스레드를 나눈다. 이미지 받기 · OCR 이 알림 시각을 밀지 않도록.
        Thread thread = new Thread(task, "limbus-maintenance");
        thread.setDaemon(true);
        return thread;
    });

    private final JDA jda;

    private final NoticeStore store;

    MaintenanceAlert(JDA jda, NoticeStore store) {
        this.jda = jda;
        this.store = store;
    }

    void restore() throws SQLException {
        for (Maintenance maintenance : store.findMaintenanceEndingAfter(Instant.now())) {
            arm(maintenance);
        }
    }

    // 이미지를 앞에서부터 읽다 시각이 나오면 멈춘다. 첫 이미지가 서신인 주가 있다.
    void register(String gid, LocalDate day, List<byte[]> images) throws SQLException {
        Maintenance maintenance = resolve(gid, day, images.stream().map(image -> ocr(gid, image)));
        store.saveMaintenance(maintenance);
        log.info("정기 점검 알림 예약 · gid={} {} ~ {}", gid, maintenance.startsAt(), maintenance.endsAt());
        arm(maintenance);
    }

    // 알림이 늦게 가는 것은 의미가 없다. 꺼져 있던 사이 지난 시각은 버린다.
    private void arm(Maintenance maintenance) {
        long end = maintenance.endsAt().getEpochSecond();
        schedule(maintenance.startsAt(), "림버스 컴퍼니 정기 점검 시작 · 종료 예정 <t:" + end + ":t> (<t:" + end + ":R>)");
        schedule(maintenance.endsAt(), "림버스 컴퍼니 정기 점검 종료 · 업데이트 적용");
    }

    private void schedule(Instant at, String text) {
        long delay = Duration.between(Instant.now(), at).toMillis();
        if (delay > 0) {
            executor.schedule(() -> announce(text), delay, TimeUnit.MILLISECONDS);
        }
    }

    private void announce(String text) {
        List<NoticeChannel> channels;
        try {
            channels = store.findChannels();
        } catch (SQLException e) {
            log.warn("점검 알림 채널 조회 실패 · {}", text, e);
            return;
        }
        for (NoticeChannel target : channels) {
            // executor 는 태스크 예외를 future 에 담아 삼키므로 여기서 남긴다
            try {
                GuildMessageChannel channel = jda.getChannelById(GuildMessageChannel.class, target.channelId());
                if (channel == null) {
                    log.warn("점검 알림 채널을 찾지 못함 · guild={} channel={}", target.guildId(), target.channelId());
                    continue;
                }
                String mention = NoticeListener.mention(target);
                NoticeListener.allowMention(channel.sendMessage(mention.isEmpty() ? text : mention + " " + text), target)
                        .complete();
            } catch (RuntimeException e) {
                log.warn("점검 알림 전송 실패 · guild={} channel={}", target.guildId(), target.channelId(), e);
            }
        }
    }

    static Optional<LocalDate> date(String title) {
        Matcher matcher = TITLE.matcher(title);
        if (!matcher.matches()) {
            return Optional.empty();
        }
        try {
            return Optional.of(LocalDate.of(group(matcher, 1), group(matcher, 2), group(matcher, 3)));
        } catch (DateTimeException e) {
            return Optional.empty();
        }
    }

    // 다른 날짜의 시각(지난 공지 인용 등) · OCR 오독으로 생긴 없는 시각은 건너뛴다
    static Optional<Maintenance> window(String gid, LocalDate day, String text) {
        Matcher matcher = WINDOW.matcher(text);
        while (matcher.find()) {
            if (group(matcher, 1) != day.getMonthValue() || group(matcher, 2) != day.getDayOfMonth()) {
                continue;
            }
            try {
                LocalTime start = LocalTime.of(group(matcher, 3), group(matcher, 4));
                LocalTime end = LocalTime.of(group(matcher, 5), group(matcher, 6));
                if (end.isAfter(start)) {
                    return Optional.of(new Maintenance(gid, at(day, start), at(day, end)));
                }
            } catch (DateTimeException e) {
                // 25:00 같은 오독
            }
        }
        return Optional.empty();
    }

    // texts 는 지연 스트림이다. 시각을 찾은 이미지 뒤로는 OCR 을 돌리지 않는다.
    static Maintenance resolve(String gid, LocalDate day, Stream<String> texts) {
        return texts.map(text -> window(gid, day, text))
                .flatMap(Optional::stream)
                .findFirst()
                .orElseGet(() -> new Maintenance(gid, at(day, USUAL_START), at(day, USUAL_END)));
    }

    // tesseract 는 컨테이너 이미지에만 있다. 없거나 실패하면 빈 텍스트라 평소 시각으로 간다.
    private static String ocr(String gid, byte[] image) {
        try {
            Process process = new ProcessBuilder("tesseract", "stdin", "stdout", "-l", "kor")
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            try (OutputStream input = process.getOutputStream()) {
                input.write(image);
            }
            String text = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!process.waitFor(OCR_TIMEOUT.toSeconds(), TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IOException("tesseract 시간 초과");
            }
            return text;
        } catch (IOException e) {
            log.warn("점검 시각 OCR 실패 · gid={} · 평소 시각 사용", gid, e);
            return "";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "";
        }
    }

    private static Instant at(LocalDate day, LocalTime time) {
        return day.atTime(time).atZone(KST).toInstant();
    }

    private static int group(Matcher matcher, int index) {
        return Integer.parseInt(matcher.group(index));
    }
}
