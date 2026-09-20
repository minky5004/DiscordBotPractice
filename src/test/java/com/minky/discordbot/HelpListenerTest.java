package com.minky.discordbot;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HelpListenerTest {

    @Test
    void helpListsEveryCommandWithItsRequiredOptionsOnly() {
        List<SlashCommandData> commands = List.of(
                Commands.slash("ping", "핑퐁"),
                Commands.slash("환상체", "환상체 도감").addOptions(
                        new OptionData(OptionType.STRING, "이름", "환상체 이름", true, true),
                        // 선택 옵션은 한 줄을 늘리기만 한다
                        new OptionData(OptionType.BOOLEAN, "공개", "채널에 공개", false)),
                Commands.slash("엔케팔린", "완충 시각").addOptions(
                        new OptionData(OptionType.INTEGER, "현재", "현재 수치", true),
                        new OptionData(OptionType.INTEGER, "최대", "최대 수치", true)));

        assertEquals("""
                        **쓸 수 있는 명령**
                        `/ping` — 핑퐁
                        `/환상체 <이름>` — 환상체 도감
                        `/엔케팔린 <현재> <최대>` — 완충 시각""",
                HelpListener.help(commands));
    }

    @Test
    void adminAndGuildOnlyCommandsDropOutWhereTheCallerCannotUseThem() {
        SlashCommandData ping = Commands.slash("ping", "핑퐁");
        SlashCommandData notice = Commands.slash("공지채널", "공지 채널 설정")
                .setContexts(InteractionContextType.GUILD)
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_SERVER));
        SlashCommandData enkephalin = Commands.slash("엔케팔린", "완충 시각")
                .setContexts(InteractionContextType.GUILD);
        List<SlashCommandData> all = List.of(ping, notice, enkephalin);

        assertEquals(all, HelpListener.usable(all, true, Set.of(Permission.MANAGE_SERVER)));
        // 서버 안이어도 권한 없는 사람에게는 관리자 명령이 슬래시 창에 뜨지 않는다
        assertEquals(List.of(ping, enkephalin), HelpListener.usable(all, true, Set.of()));
        // 봇 DM 에서는 서버 전용 명령 둘 다 빠진다
        assertEquals(List.of(ping), HelpListener.usable(all, false, Set.of()));
    }

    @Test
    void helpStaysInsideOneMessage() {
        // 개인 메시지 하나에 들어가야 한다 · 명령이 늘어도 잘려서 갈 뿐 전송이 실패하지 않는다
        List<SlashCommandData> many = IntStream.range(0, 200)
                .mapToObj(i -> Commands.slash("명령" + i, "설명이 제법 긴 명령 " + i))
                .map(SlashCommandData.class::cast)
                .toList();

        assertTrue(HelpListener.help(many).length() <= Message.MAX_CONTENT_LENGTH);
    }
}
