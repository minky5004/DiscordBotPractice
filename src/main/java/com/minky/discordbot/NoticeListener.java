package com.minky.discordbot;

import com.minky.discordbot.NoticeStore.NoticeChannel;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Message.MentionType;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.api.utils.data.DataArray;
import net.dv8tion.jda.api.utils.data.DataObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

// 림버스 컴퍼니 Steam 공식 공지를 서버별 설정 채널로 중계한다
public class NoticeListener extends ListenerAdapter {

    private static final Logger log = LoggerFactory.getLogger(NoticeListener.class);

    // 림버스 컴퍼니 Steam 앱
    private static final String APP_ID = "1973530";

    // 림버스 컴퍼니 Steam 그룹. 이미지 경로의 클랜 ID 와 같다.
    private static final String CLAN_ID = "43587230";

    // 공식 뉴스 API(ISteamNews)는 언어 인자를 무시해 영어만 준다. 한국어 본문 · 이미지는 문서 없는 스토어 이벤트 API 에만 있다.
    // 고정 공지가 목록 앞을 차지하므로 넉넉히 받는다.
    private static final URI FEED = URI.create("https://store.steampowered.com/events/ajaxgetpartnereventspageable/?clan_accountid="
            + CLAN_ID + "&appid=" + APP_ID + "&offset=0&count=20&l=koreana");

    private static final String CLAN_IMAGES = "https://clan.akamai.steamstatic.com/images";

    private static final Pattern CLAN_IMAGE = Pattern.compile("\\[img]\\{STEAM_CLAN_IMAGE\\}(.*?)\\[/img]");

    private static final Pattern ANY_IMAGE = Pattern.compile("\\[img].*?\\[/img]", Pattern.DOTALL);

    private static final Pattern VIDEO = Pattern.compile("\\[previewyoutube=([\\w-]+)[^]]*]\\[/previewyoutube]");

    // Steam BBCode 태그만 지운다. 본문에 [Hotfix] · [Notice] 같은 글자 그대로의 대괄호가 흔하다.
    private static final Pattern TAG = Pattern.compile(
            "\\[/?(?:p|b|i|u|s|strike|spoiler|noparse|h[1-6]|hr|list|olist|quote|code|table|tr|td|th|url)(?:=[^]]*)?]",
            Pattern.CASE_INSENSITIVE);

    private static final Duration POLL_INTERVAL = Duration.ofMinutes(10);

    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(10);

    private static final String CHANNEL = "채널";
    private static final String ROLE = "역할";

    private static final DefaultMemberPermissions ADMIN = DefaultMemberPermissions.enabledFor(Permission.MANAGE_SERVER);

    static final SlashCommandData COMMAND = Commands.slash("공지채널", "림버스 컴퍼니 Steam 공지를 받을 채널 설정")
            .setContexts(InteractionContextType.GUILD)
            .setDefaultPermissions(ADMIN)
            .addOptions(
                    new OptionData(OptionType.CHANNEL, CHANNEL, "공지를 올릴 채널", true)
                            .setChannelTypes(ChannelType.TEXT, ChannelType.NEWS),
                    new OptionData(OptionType.ROLE, ROLE, "공지마다 멘션할 역할", false));

    static final SlashCommandData CANCEL = Commands.slash("공지채널해제", "림버스 컴퍼니 공지 중계 해제")
            .setContexts(InteractionContextType.GUILD)
            .setDefaultPermissions(ADMIN);

    record Notice(String gid, String title, long date, String contents) {

        String url() {
            return "https://store.steampowered.com/news/app/" + APP_ID + "/view/" + gid;
        }
    }

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(HTTP_TIMEOUT).build();

    // JDA 가 내려간 뒤 JVM 이 이 스레드에 붙들리지 않게 데몬으로
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "limbus-notice");
        thread.setDaemon(true);
        return thread;
    });

    private final JDA jda;

    private final NoticeStore store;

    private final MaintenanceAlert maintenance;

    NoticeListener(JDA jda, NoticeStore store, MaintenanceAlert maintenance) {
        this.jda = jda;
        this.store = store;
        this.maintenance = maintenance;
    }

    void start() {
        executor.scheduleWithFixedDelay(this::poll, 0, POLL_INTERVAL.toMinutes(), TimeUnit.MINUTES);
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (CANCEL.getName().equals(event.getName())) {
            cancel(event);
            return;
        }
        if (!COMMAND.getName().equals(event.getName())) {
            return;
        }
        GuildMessageChannel channel = event.getOption(CHANNEL).getAsChannel().asGuildMessageChannel();
        Role role = event.getOption(ROLE, OptionMapping::getAsRole);
        // 임베드 · 이미지 첨부 권한이 없으면 전송이 거부되고, 이미 기록된 공지라 그 채널엔 다시 오지 않는다
        if (!channel.canTalk() || !event.getGuild().getSelfMember()
                .hasPermission(channel, Permission.MESSAGE_EMBED_LINKS, Permission.MESSAGE_ATTACH_FILES)) {
            event.reply(channel.getAsMention() + " · 봇의 메시지 보내기 · 링크 첨부 · 파일 첨부 권한 필요").setEphemeral(true).queue();
            return;
        }

        long guildId = event.getGuild().getIdLong();
        try {
            store.setChannel(new NoticeChannel(guildId, channel.getIdLong(), role == null ? null : role.getIdLong()));
        } catch (SQLException e) {
            log.warn("공지 채널 설정 실패 · guild={}", guildId, e);
            event.reply("공지 채널 설정 실패 · 잠시 뒤 재시도").setEphemeral(true).queue();
            return;
        }

        String reply = "림버스 컴퍼니 Steam 공지 채널 " + channel.getAsMention();
        if (role != null) {
            reply += " · 멘션 " + role.getAsMention();
            // 멘션 불가 역할은 글자만 찍히고 알림이 가지 않는다
            if (!role.isMentionable() && !event.getGuild().getSelfMember().hasPermission(Permission.MESSAGE_MENTION_EVERYONE)) {
                reply += "\n-# 역할 멘션 불가 상태 · 역할 설정의 멘션 허용 또는 봇의 @everyone 멘션 권한 필요";
            }
        }
        event.reply(reply + "\n-# 해제는 /" + CANCEL.getName()).setEphemeral(true).queue();
    }

    private void cancel(SlashCommandInteractionEvent event) {
        long guildId = event.getGuild().getIdLong();
        try {
            boolean had = store.removeChannel(guildId);
            event.reply(had ? "림버스 컴퍼니 공지 중계 해제" : "설정된 공지 채널 없음").setEphemeral(true).queue();
        } catch (SQLException e) {
            log.warn("공지 채널 해제 실패 · guild={}", guildId, e);
            event.reply("공지 채널 해제 실패 · 잠시 뒤 재시도").setEphemeral(true).queue();
        }
    }

    private void poll() {
        try {
            List<Notice> notices = parse(fetch());
            // 기록 전에 읽는다. 기록 뒤에 실패하면 그 공지는 다시 새 공지로 잡히지 않는다.
            List<NoticeChannel> channels = store.findChannels();
            for (Notice notice : store.markNew(notices)) {
                // 채널마다 다시 받지 않도록 공지당 한 번
                List<Image> images = download(notice);
                for (NoticeChannel channel : channels) {
                    // 이미 기록된 공지다. 한 건이 터져 루프를 벗어나면 남은 공지 · 채널이 영영 빠진다.
                    try {
                        post(notice, images, channel);
                    } catch (RuntimeException e) {
                        log.warn("공지 전송 실패 · guild={} channel={} gid={}",
                                channel.guildId(), channel.channelId(), notice.gid(), e);
                    }
                }
            }
            registerMaintenance(notices);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException | SQLException | RuntimeException e) {
            // scheduleWithFixedDelay 는 예외로 끝난 태스크를 다시 돌리지 않는다. 여기서 삼켜야 다음 주기가 온다.
            log.warn("Steam 공지 확인 실패", e);
        }
    }

    // 새 공지 판정과 따로 본다. 공지가 올라온 뒤 처음 켠 봇 · 알림 없이 기록만 한 첫 기동도 그 주 점검은 잡도록.
    private void registerMaintenance(List<Notice> notices) throws SQLException, InterruptedException {
        LocalDate today = LocalDate.now(MaintenanceAlert.KST);
        for (Notice notice : notices) {
            Optional<LocalDate> day = MaintenanceAlert.date(notice.title());
            if (day.isPresent() && !day.get().isBefore(today) && maintenance.wants(notice.gid())) {
                List<Image> images = download(notice);
                // 빠진 이미지에 시각이 있었을 수 있다. 그 결과는 확정하지 않고 다음 폴에서 다시 읽는다.
                boolean allDownloaded = images.size() == images(notice.contents()).size();
                maintenance.register(notice.gid(), day.get(), images.stream().map(Image::data).toList(), allDownloaded);
            }
        }
    }

    private String fetch() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(FEED).timeout(HTTP_TIMEOUT).build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("Steam 응답 " + response.statusCode());
        }
        return response.body();
    }

    private record Image(String name, byte[] data) {
    }

    // 받지 못한 이미지는 빼고 보낸다. 이미지 하나 때문에 이미 기록된 공지를 버리지 않는다.
    private List<Image> download(Notice notice) throws InterruptedException {
        List<Image> images = new ArrayList<>();
        for (String url : images(notice.contents())) {
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(HTTP_TIMEOUT).build();
                HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
                if (response.statusCode() != 200) {
                    throw new IOException("응답 " + response.statusCode());
                }
                images.add(new Image(url.substring(url.lastIndexOf('/') + 1), response.body()));
            } catch (IOException | RuntimeException e) {
                // URI.create 의 IllegalArgumentException 도 여기서. 밖으로 새면 기록된 공지와 뒤 공지가 전부 빠진다.
                log.warn("공지 이미지 받기 실패 · gid={} url={}", notice.gid(), url, e);
            }
        }
        return images;
    }

    private void post(Notice notice, List<Image> images, NoticeChannel target) {
        GuildMessageChannel channel = jda.getChannelById(GuildMessageChannel.class, target.channelId());
        if (channel == null) {
            log.warn("공지 채널을 찾지 못함 · guild={} channel={}", target.guildId(), target.channelId());
            return;
        }
        List<String> lines = new ArrayList<>(videos(notice.contents()));
        String mention = mention(target);
        if (!mention.isEmpty()) {
            lines.addFirst(mention);
        }
        // 첨부 파일은 디스코드가 격자 갤러리로 묶어 임베드 위에 보인다.
        // 업로드 한도는 요청 합계가 아니라 파일 한 장마다다. 인격 공지는 한 장 2MB 대 6장이라 합계로 재면 뒤 장이 빠진다.
        long maxFileSize = channel.getGuild().getMaxFileSize();
        List<FileUpload> files = images.stream()
                .filter(image -> image.data().length <= maxFileSize)
                .map(image -> FileUpload.fromData(image.data(), image.name()))
                .toList();
        MessageCreateAction message = channel.sendMessageEmbeds(embed(notice)).addFiles(files);
        if (!lines.isEmpty()) {
            // 유튜브 링크는 본문에 있어야 디스코드가 영상 플레이어를 붙인다
            message.setContent(String.join("\n", lines));
        }
        // 전송이 끝난 뒤에 다음 공지로 넘어간다. 큰 업로드가 뒤 공지와 겹쳐 순서가 섞이지 않도록. 실패는 poll 루프가 남긴다.
        allowMention(message, target).complete();
    }

    // @everyone 역할은 ID 가 서버 ID 와 같고, <@&서버ID> 로는 알림 없이 "@@everyone" 으로 찍힌다
    static String mention(NoticeChannel target) {
        Long roleId = target.roleId();
        if (roleId == null) {
            return "";
        }
        return roleId == target.guildId() ? "@everyone" : "<@&" + roleId + ">";
    }

    // 공지 본문은 외부 입력이다. 설정한 역할 외의 멘션은 막는다.
    static MessageCreateAction allowMention(MessageCreateAction message, NoticeChannel target) {
        Long roleId = target.roleId();
        if (roleId != null && roleId == target.guildId()) {
            return message.setAllowedMentions(EnumSet.of(MentionType.EVERYONE));
        }
        message.setAllowedMentions(EnumSet.noneOf(MentionType.class));
        return roleId == null ? message : message.mentionRoles(roleId);
    }

    // 이미지는 첨부 파일로 가므로 임베드에는 제목 · 본문만
    static MessageEmbed embed(Notice notice) {
        return new EmbedBuilder()
                .setTitle(cut(notice.title(), MessageEmbed.TITLE_MAX_LENGTH), notice.url())
                .setDescription(text(notice.contents()))
                .build();
    }

    static List<String> images(String contents) {
        return CLAN_IMAGE.matcher(contents).results()
                .map(match -> CLAN_IMAGES + match.group(1))
                .limit(Message.MAX_FILE_AMOUNT)
                .toList();
    }

    static List<String> videos(String contents) {
        return VIDEO.matcher(contents).results().map(match -> "https://youtu.be/" + match.group(1)).toList();
    }

    static String text(String contents) {
        String text = contents.replace("\r", "");
        text = ANY_IMAGE.matcher(text).replaceAll("");
        text = VIDEO.matcher(text).replaceAll("");
        text = text.replace("[*]", "\n• ").replaceAll("(?i)\\[/p]", "\n");
        text = TAG.matcher(text).replaceAll("");
        text = text.replaceAll("\n{3,}", "\n\n").strip();
        return cut(text, MessageEmbed.DESCRIPTION_MAX_LENGTH);
    }

    private static String cut(String text, int limit) {
        return text.length() <= limit ? text : text.substring(0, limit - 1) + "…";
    }

    // 이벤트 목록은 고정 공지가 앞에 오는 등 날짜순이 아니다. 밀린 공지가 여러 건이면 올라온 순서대로 보내도록 정렬한다.
    static List<Notice> parse(String json) {
        DataArray events = DataObject.fromJson(json).getArray("events");
        List<Notice> notices = new ArrayList<>();
        for (int i = 0; i < events.length(); i++) {
            DataObject event = events.getObject(i);
            DataObject body = event.getObject("announcement_body");
            notices.add(new Notice(event.getString("gid"), event.getString("event_name"), body.getLong("posttime"),
                    body.getString("body", "")));
        }
        notices.sort(Comparator.comparingLong(Notice::date));
        return notices;
    }
}
