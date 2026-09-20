package com.minky.discordbot;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

// 등록된 명령 전부를 개인 메시지로 보내는 도움말
public class HelpListener extends ListenerAdapter {

    static final SlashCommandData COMMAND = Commands.slash("help", "쓸 수 있는 명령 전부를 개인 메시지로");

    // 등록 목록 그대로라 도움말용 문구를 따로 두지 않는다 · 게임 폴더 없이 뜬 봇에서는 게임 데이터 명령이 저절로 빠진다
    private final List<SlashCommandData> commands;

    HelpListener(List<SlashCommandData> commands) {
        this.commands = commands;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!COMMAND.getName().equals(event.getName())) {
            return;
        }
        String help = help(commands);
        // DM 채널 열기 · 보내기가 REST 두 번이라 3초 응답 기한을 넘길 수 있다
        event.deferReply(true).queue();
        event.getUser().openPrivateChannel()
                .flatMap(channel -> channel.sendMessage(help))
                .queue(sent -> event.getHook().sendMessage("개인 메시지로 보냈습니다").queue(),
                        // DM 을 막아 둔 사람에게 침묵으로 끝나지 않게 같은 내용을 여기로
                        error -> event.getHook().sendMessage(help).queue());
    }

    static String help(List<SlashCommandData> commands) {
        List<String> lines = new ArrayList<>();
        lines.add("**쓸 수 있는 명령**");
        for (SlashCommandData command : commands) {
            lines.add("`/" + command.getName() + required(command) + "` — " + command.getDescription());
        }
        return NoticeListener.cut(String.join("\n", lines), Message.MAX_CONTENT_LENGTH);
    }

    // 필수 옵션만 자리를 보여 준다 · 선택 옵션까지 넣으면 한 줄이 화면을 넘는다
    private static String required(SlashCommandData command) {
        return command.getOptions().stream()
                .filter(OptionData::isRequired)
                .map(option -> " <" + option.getName() + ">")
                .collect(Collectors.joining());
    }
}
