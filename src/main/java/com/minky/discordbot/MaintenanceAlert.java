package com.minky.discordbot;

import com.minky.discordbot.NoticeStore.NoticeChannel;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
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

    // 점검 시각은 공지 이미지 안에만 있다. "2026년 9월 17일 06:00 ~ 12:00 (KST)에 정기 점검이 …" · "9월 17일(목) 06:00 ~"
    private static final Pattern WINDOW = Pattern.compile(
            "(\\d{1,2})월\\s*(\\d{1,2})일\\s*(?:\\([^)]*\\)\\s*)?(\\d{1,2}):(\\d{2})\\s*~\\s*(\\d{1,2}):(\\d{2})");

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

    // 평소 시각으로 둔 행을 다시 읽어 덮어쓰면 앞서 건 예약을 취소해야 알림이 두 번 가지 않는다
    private final Map<String, List<ScheduledFuture<?>>> armed = new ConcurrentHashMap<>();

    private final JDA jda;

    private final NoticeStore store;

    // 컨테이너 이미지에만 있다. 없는 봇(로컬 gradlew run)은 미확정 행을 다시 읽으려 폴마다 이미지를 받지 않는다.
    private final boolean ocrAvailable = tesseractInstalled();

    MaintenanceAlert(JDA jda, NoticeStore store) {
        this.jda = jda;
        this.store = store;
    }

    void restore() throws SQLException {
        for (Maintenance maintenance : store.findMaintenanceEndingAfter(Instant.now())) {
            arm(maintenance);
        }
    }

    // 처음 보는 공지 · OCR 이 되는 봇에서 만난 미확정 행만
    boolean wants(String gid) throws SQLException {
        Optional<Boolean> confirmed = store.findMaintenanceConfirmed(gid);
        return confirmed.isEmpty() || (!confirmed.get() && ocrAvailable);
    }

    // 이미지를 앞에서부터 읽다 시각이 나오면 멈춘다. 첫 이미지가 서신인 주가 있다.
    void register(String gid, LocalDate day, List<byte[]> images, boolean allDownloaded) throws SQLException {
        AtomicBoolean ocrFailed = new AtomicBoolean();
        Optional<Maintenance> read = resolve(gid, day, images.stream()
                .takeWhile(image -> !ocrFailed.get())
                .map(image -> ocr(gid, image).orElseGet(() -> {
                    ocrFailed.set(true);
                    return "";
                })));
        // 이미지를 다 받아 다 읽고도 시각이 없을 때만 평소 시각이 확정이다. 실패로 못 읽은 결과를 굳히면
        // 06:00 시작인 주에 10:00 알림이 영영 남는다.
        boolean confirmed = read.isPresent() || (allDownloaded && !ocrFailed.get());
        Maintenance maintenance = read.orElseGet(() -> usual(gid, day));
        store.saveMaintenance(maintenance, confirmed);
        log.info("정기 점검 알림 예약 · gid={} {} ~ {} · confirmed={}", gid, maintenance.startsAt(), maintenance.endsAt(), confirmed);
        arm(maintenance);
    }

    // 알림이 늦게 가는 것은 의미가 없다. 꺼져 있던 사이 지난 시각은 버린다.
    private void arm(Maintenance maintenance) {
        long end = maintenance.endsAt().getEpochSecond();
        List<ScheduledFuture<?>> futures = new ArrayList<>();
        schedule(maintenance.startsAt(), "림버스 컴퍼니 정기 점검 시작 · 종료 예정 <t:" + end + ":t> (<t:" + end + ":R>)")
                .ifPresent(futures::add);
        schedule(maintenance.endsAt(), "림버스 컴퍼니 정기 점검 종료 · 업데이트 적용").ifPresent(futures::add);
        List<ScheduledFuture<?>> replaced = armed.put(maintenance.gid(), futures);
        if (replaced != null) {
            replaced.forEach(future -> future.cancel(false));
        }
    }

    private Optional<ScheduledFuture<?>> schedule(Instant at, String text) {
        long delay = Duration.between(Instant.now(), at).toMillis();
        if (delay <= 0) {
            return Optional.empty();
        }
        return Optional.of(executor.schedule(() -> announce(text), delay, TimeUnit.MILLISECONDS));
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
    static Optional<Maintenance> resolve(String gid, LocalDate day, Stream<String> texts) {
        return texts.map(text -> window(gid, day, text))
                .flatMap(Optional::stream)
                .findFirst();
    }

    static Maintenance usual(String gid, LocalDate day) {
        return new Maintenance(gid, at(day, USUAL_START), at(day, USUAL_END));
    }

    // 파이프 대신 파일로 주고받는다. stdout 을 끝까지 읽는 동안 막히면 waitFor 의 시간 제한이 걸리지 않고
    // 공지 폴러 스레드가 그대로 멈춘다.
    private static Optional<String> ocr(String gid, byte[] image) {
        Path input = null;
        Path output = null;
        try {
            input = Files.createTempFile("notice", ".img");
            output = Files.createTempFile("ocr", ".txt");
            Files.write(input, image);
            Process process = new ProcessBuilder("tesseract", input.toString(), "stdout", "-l", "kor")
                    .redirectOutput(output.toFile())
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            if (!process.waitFor(OCR_TIMEOUT.toSeconds(), TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IOException("tesseract 시간 초과");
            }
            if (process.exitValue() != 0) {
                throw new IOException("tesseract 종료 코드 " + process.exitValue());
            }
            return Optional.of(Files.readString(output));
        } catch (IOException e) {
            log.warn("점검 시각 OCR 실패 · gid={}", gid, e);
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } finally {
            delete(input);
            delete(output);
        }
    }

    private static boolean tesseractInstalled() {
        try {
            Process process = new ProcessBuilder("tesseract", "--version")
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start();
            if (!process.waitFor(OCR_TIMEOUT.toSeconds(), TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return false;
            }
            return process.exitValue() == 0;
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static void delete(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("OCR 임시 파일 삭제 실패 · {}", path, e);
        }
    }

    private static Instant at(LocalDate day, LocalTime time) {
        return day.atTime(time).atZone(KST).toInstant();
    }

    private static int group(Matcher matcher, int index) {
        return Integer.parseInt(matcher.group(index));
    }
}
