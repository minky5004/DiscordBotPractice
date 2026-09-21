package com.minky.discordbot;

import com.minky.discordbot.IdentityCatalog.Keyword;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.container.ContainerChildComponent;
import net.dv8tion.jda.api.components.separator.Separator;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// 림버스 컴퍼니 전투 키워드(상태 효과) 사전
public class KeywordListener extends ListenerAdapter {

    private static final String NAME = "이름";
    private static final String OPEN = "공개";

    private static final int USER_LIMIT = 8;

    static final SlashCommandData COMMAND = Commands.slash("키워드", "림버스 컴퍼니 전투 키워드 설명")
            .addOptions(
                    new OptionData(OptionType.STRING, NAME, "화상 · 침잠 같은 키워드 이름", true, true),
                    new OptionData(OptionType.BOOLEAN, OPEN, "채널에 공개 · 기본 나만 보기", false));

    private final IdentityCatalog catalog;

    KeywordListener(IdentityCatalog catalog) {
        this.catalog = catalog;
    }

    @Override
    public void onCommandAutoCompleteInteraction(CommandAutoCompleteInteractionEvent event) {
        if (!COMMAND.getName().equals(event.getName())) {
            return;
        }
        event.replyChoices(search(catalog.keywords(), event.getFocusedOption().getValue()).stream()
                .map(keyword -> new Command.Choice(NoticeListener.cut(keyword.name(), OptionData.MAX_CHOICE_NAME_LENGTH), keyword.id()))
                .toList()).queue();
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!COMMAND.getName().equals(event.getName())) {
            return;
        }
        List<Keyword> keywords = catalog.keywords();
        Keyword keyword = find(keywords, event.getOption(NAME).getAsString());
        if (keyword == null) {
            // 기동 직후엔 목록이 아직 비어 있다
            event.reply(keywords.isEmpty() ? "키워드 목록을 아직 읽지 못했습니다 · 잠시 뒤 다시" : "찾지 못한 키워드 · 자동완성 후보에서 선택")
                    .setEphemeral(true).queue();
            return;
        }
        event.replyComponents(container(keyword))
                .useComponentsV2()
                .setEphemeral(!event.getOption(OPEN, false, OptionMapping::getAsBoolean))
                .queue();
    }

    // /인격 화면의 키워드 메뉴 · 인격 화면은 그대로 두고 설명만 따로
    @Override
    public void onStringSelectInteraction(StringSelectInteractionEvent event) {
        if (!COMMAND.getName().equals(event.getComponentId())) {
            return;
        }
        // 값은 키워드 ID 로만 찾는다 · 24시간 갱신으로 빠진 키워드가 글자 검색으로 다른 키워드에 걸리지 않게
        String id = event.getValues().getFirst();
        Keyword keyword = catalog.keywords().stream().filter(k -> k.id().equals(id)).findFirst().orElse(null);
        if (keyword == null) {
            event.reply("키워드 목록에 없는 항목 · 명령을 다시 실행").setEphemeral(true).queue();
            return;
        }
        // 새 응답만 보내면 메뉴에 고른 값이 남아 같은 키워드를 다시 고를 수 없다 · 같은 화면으로 편집해 선택을 비우고 설명은 후속 메시지로
        event.editComponents(event.getMessage().getComponentTree()).useComponentsV2()
                .flatMap(hook -> hook.sendMessageComponents(container(keyword)).useComponentsV2().setEphemeral(true))
                .queue();
    }

    static Container container(Keyword keyword) {
        List<ContainerChildComponent> children = new ArrayList<>(List.of(
                TextDisplay.of("## " + keyword.name()),
                Separator.createDivider(Separator.Spacing.SMALL),
                TextDisplay.of(NoticeListener.cut(keyword.desc(), IdentityListener.TEXT_LIMIT))));
        // 디스코드는 빈 TextDisplay 를 거부한다
        if (!keyword.users().isEmpty()) {
            children.add(TextDisplay.of(users(keyword.users())));
        }
        return Container.of(children).withAccentColor(IdentityListener.DEFAULT_COLOR);
    }

    // 화상 · 출혈 같은 공통 키워드는 인격 수십이 쓴다 · 앞 몇만 이름으로
    static String users(List<String> users) {
        String names = String.join(" · ", users.subList(0, Math.min(users.size(), USER_LIMIT)));
        String rest = users.size() > USER_LIMIT ? " 외 " + (users.size() - USER_LIMIT) : "";
        return NoticeListener.cut("-# 사용  " + names + rest, IdentityListener.TEXT_LIMIT);
    }

    // 자동완성 후보를 고르면 값이 키워드 ID, 직접 친 글자면 첫 일치
    static Keyword find(List<Keyword> keywords, String query) {
        return keywords.stream()
                .filter(keyword -> keyword.id().equals(query))
                .findFirst()
                .orElseGet(() -> search(keywords, query).stream().findFirst().orElse(null));
    }

    static List<Keyword> search(List<Keyword> keywords, String query) {
        String needle = compact(query);
        return keywords.stream()
                .filter(keyword -> compact(keyword.name()).contains(needle))
                .limit(OptionData.MAX_CHOICES)
                .toList();
    }

    private static String compact(String text) {
        return text.replaceAll("\\s", "").toLowerCase(Locale.ROOT);
    }
}
