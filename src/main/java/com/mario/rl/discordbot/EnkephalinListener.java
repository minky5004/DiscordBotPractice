package com.mario.rl.discordbot;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.time.Duration;
import java.time.Instant;

public class EnkephalinListener extends ListenerAdapter {

    // 엔케팔린 1개 충전 주기. 게임 패치로 바뀌면 여기만 고친다.
    private static final Duration CHARGE_INTERVAL = Duration.ofMinutes(6);

    // getAsInt() 는 Math.toIntExact 라 int 를 넘기면 잘리지 않고 터진다. 어떤 관리자 레벨의 캡보다도 위.
    private static final int INPUT_CEILING = 999;

    private static final String CURRENT = "현재";
    private static final String MAX = "최대";

    static final SlashCommandData COMMAND = Commands.slash("엔케팔린", "완충까지 남은 시간 계산")
            .addOptions(
                    new OptionData(OptionType.INTEGER, CURRENT, "지금 보유한 엔케팔린", true)
                            .setRequiredRange(0, INPUT_CEILING),
                    new OptionData(OptionType.INTEGER, MAX, "관리자 레벨에 따른 최대치", true)
                            .setRequiredRange(1, INPUT_CEILING));

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
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
        long fullAt = Instant.now().plus(remaining).getEpochSecond();
        event.reply(held + " · 완충 <t:" + fullAt + ":f> (<t:" + fullAt + ":R>)").queue();
    }

    static Duration untilFull(int current, int max) {
        if (current >= max) {
            return Duration.ZERO;
        }
        return CHARGE_INTERVAL.multipliedBy(max - current);
    }
}
