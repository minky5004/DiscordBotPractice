package com.minky.discordbot;

import com.minky.discordbot.ReminderScheduler.Reminder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.UserSnowflake;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;

public class EnkephalinListener extends ListenerAdapter {

    private static final Logger log = LoggerFactory.getLogger(EnkephalinListener.class);

    // 엔케팔린 1개 충전 주기. 게임 패치로 바뀌면 여기만 고친다.
    private static final Duration CHARGE_INTERVAL = Duration.ofMinutes(6);

    // getAsInt() 는 Math.toIntExact 라 int 를 넘기면 잘리지 않고 터진다. 어떤 관리자 레벨의 캡보다도 위.
    private static final int INPUT_CEILING = 999;

    private static final String CURRENT = "현재";
    private static final String MAX = "최대";
    private static final String ALERT = "알림";

    // 재시작 뒤 멘션 채널을 길드 캐시에서 다시 찾는다. 봇 DM 채널은 캐시에 없어 복구할 수 없으니 서버에서만.
    static final SlashCommandData COMMAND = Commands.slash("엔케팔린", "완충까지 남은 시간 계산")
            .setContexts(InteractionContextType.GUILD)
            .addOptions(
                    new OptionData(OptionType.INTEGER, CURRENT, "지금 보유한 엔케팔린", true)
                            .setRequiredRange(0, INPUT_CEILING),
                    new OptionData(OptionType.INTEGER, MAX, "관리자 레벨에 따른 최대치", true)
                            .setRequiredRange(1, INPUT_CEILING),
                    new OptionData(OptionType.BOOLEAN, ALERT, "완충 시각에 멘션 · 기본 켜짐", false));

    static final SlashCommandData CANCEL = Commands.slash("엔케팔린취소", "예약한 완충 알림 취소")
            .setContexts(InteractionContextType.GUILD);

    private final JDA jda;

    private final ReminderScheduler reminders;

    EnkephalinListener(JDA jda, ReminderStore store) {
        this.jda = jda;
        this.reminders = new ReminderScheduler(store, this::mention);
    }

    void restore() throws SQLException {
        reminders.restore();
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
        int current = event.getOption(CURRENT).getAsInt();
        int max = event.getOption(MAX).getAsInt();
        String held = "엔케팔린 " + current + "/" + max;

        if (current > max) {
            event.reply(held + " · 최대치 초과 — 충전 정지 · 옵션 순서 확인").queue();
            return;
        }

        Duration remaining = untilFull(current, max);
        if (remaining.isZero()) {
            event.reply(held + " · 이미 완충").queue();
            return;
        }
        Instant fireAt = Instant.now().plus(remaining);
        long fullAt = fireAt.getEpochSecond();
        String reply = held + " · 완충 <t:" + fullAt + ":f> (<t:" + fullAt + ":R>)";

        // 완충 시각을 묻는 사람은 대개 알림도 원한다. 계산만 하려면 `알림:False`.
        if (event.getOption(ALERT, true, OptionMapping::getAsBoolean)) {
            long userId = event.getUser().getIdLong();
            try {
                reminders.schedule(new Reminder(userId, event.getChannel().getIdLong(), fireAt));
                reply += "\n-# 완충 시각에 멘션 · 해제는 /" + CANCEL.getName();
            } catch (SQLException e) {
                log.warn("완충 알림 예약 실패 · user={}", userId, e);
                reply += "\n-# 알림 예약 실패 · 완충 시각만 안내";
            }
        }
        event.reply(reply).queue();
    }

    private void cancel(SlashCommandInteractionEvent event) {
        long userId = event.getUser().getIdLong();
        try {
            boolean had = reminders.cancel(userId);
            event.reply(had ? "완충 알림 예약 취소" : "예약된 완충 알림 없음").queue();
        } catch (SQLException e) {
            log.warn("완충 알림 취소 실패 · user={}", userId, e);
            event.reply("완충 알림 취소 실패 · 잠시 뒤 재시도").queue();
        }
    }

    private void mention(Reminder reminder) {
        String text = UserSnowflake.fromId(reminder.userId()).getAsMention() + " 엔케팔린 완충";
        MessageChannel channel = jda.getChannelById(MessageChannel.class, reminder.channelId());
        if (channel != null) {
            channel.sendMessage(text)
                    // 몇 시간 뒤에 도는 발화라 권한 상실이 여기로 온다
                    .queue(null, error -> log.warn("완충 멘션 실패 · user={} channel={}",
                            reminder.userId(), reminder.channelId(), error));
            return;
        }
        // JDA 는 보관된 스레드를 캐시에서 빼고, 채널을 ID 로 다시 조회하는 REST 도 없다.
        // 보관 스레드 · 삭제된 채널로 캐시에서 못 찾은 예약은 버리지 않고 DM 으로 보낸다.
        jda.openPrivateChannelById(reminder.userId())
                .flatMap(dm -> dm.sendMessage(text + "\n-# 예약한 채널을 찾지 못한 알림 · DM 전달"))
                .queue(null, error -> log.warn("완충 DM 실패 · user={} channel={}",
                        reminder.userId(), reminder.channelId(), error));
    }

    static Duration untilFull(int current, int max) {
        if (current >= max) {
            return Duration.ZERO;
        }
        return CHARGE_INTERVAL.multipliedBy(max - current);
    }
}
