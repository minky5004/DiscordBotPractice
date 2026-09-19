package com.minky.discordbot;

import com.minky.discordbot.GiftCatalog.Gift;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.container.ContainerChildComponent;
import net.dv8tion.jda.api.components.section.Section;
import net.dv8tion.jda.api.components.separator.Separator;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.components.thumbnail.Thumbnail;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
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

// 거울 던전 E.G.O 기프트 조회 · 강화판은 버튼으로
public class GiftListener extends ListenerAdapter {

    private static final String WIKI_CREDIT = "-# 수치: Limbus Company Wiki (wiki.gg)";

    private static final String NAME = "이름";
    private static final String OPEN = "공개";

    private static final List<String> LEVELS = List.of("기본", "+", "++");

    static final SlashCommandData COMMAND = Commands.slash("기프트", "거울 던전 E.G.O 기프트 조회")
            .addOptions(
                    new OptionData(OptionType.STRING, NAME, "기프트 이름", true, true),
                    new OptionData(OptionType.BOOLEAN, OPEN, "채널에 공개 · 기본 나만 보기", false));

    private final IdentityCatalog catalog;

    GiftListener(IdentityCatalog catalog) {
        this.catalog = catalog;
    }

    @Override
    public void onCommandAutoCompleteInteraction(CommandAutoCompleteInteractionEvent event) {
        if (!COMMAND.getName().equals(event.getName())) {
            return;
        }
        event.replyChoices(search(catalog.gifts(), event.getFocusedOption().getValue()).stream()
                .map(gift -> new Command.Choice(NoticeListener.cut(gift.name(), OptionData.MAX_CHOICE_NAME_LENGTH), String.valueOf(gift.id())))
                .toList()).queue();
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!COMMAND.getName().equals(event.getName())) {
            return;
        }
        List<Gift> gifts = catalog.gifts();
        Gift gift = find(gifts, event.getOption(NAME).getAsString());
        if (gift == null) {
            // 기동 직후엔 목록이 아직 비어 있다
            event.reply(gifts.isEmpty() ? "기프트 목록을 아직 읽지 못했습니다 · 잠시 뒤 다시" : "찾지 못한 기프트 · 자동완성 후보에서 선택")
                    .setEphemeral(true).queue();
            return;
        }
        event.replyComponents(container(gift, 0))
                .useComponentsV2()
                .setEphemeral(!event.getOption(OPEN, false, OptionMapping::getAsBoolean))
                .queue();
    }

    // 강화 버튼 · /기프트검색 결과 버튼 둘 다 이 customId 로 온다
    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String[] parts = event.getComponentId().split(":", 3);
        if (parts.length != 3 || !COMMAND.getName().equals(parts[0])) {
            return;
        }
        // 버튼은 ID 로만 찾는다 · 글자 검색까지 타면 목록에서 빠진 기프트가 다른 기프트로 바꿔치기된다
        Gift gift = byId(catalog.gifts(), parts[1]);
        if (gift == null) {
            event.reply("기프트 목록에 없는 항목 · 명령을 다시 실행").setEphemeral(true).queue();
            return;
        }
        // 편집도 V1 이 기본이라 플래그를 다시 준다
        event.editComponents(container(gift, Integer.parseInt(parts[2]))).useComponentsV2().queue();
    }

    static Container container(Gift gift, int level) {
        // 목록에서 벗어난 단계는 기본으로 · 24시간 갱신이 강화판을 줄이면 옛 메시지의 버튼이 여기로 온다
        int shown = level >= 0 && level < gift.texts().size() ? level : 0;
        List<TextDisplay> heading = new ArrayList<>();
        heading.add(TextDisplay.of("## " + gift.name() + (shown == 0 ? "" : LEVELS.get(shown))));
        String grade = grade(gift);
        if (grade != null) {
            heading.add(TextDisplay.of("-# " + grade));
        }

        List<ContainerChildComponent> children = new ArrayList<>();
        if (gift.image() == null) {
            children.addAll(heading);
        } else {
            children.add(Section.of(Thumbnail.fromUrl(gift.image()).withDescription(gift.name()), heading));
        }
        children.add(Separator.createDivider(Separator.Spacing.SMALL));
        String text = gift.texts().get(shown);
        // 디스코드는 빈 TextDisplay 를 거부한다
        children.add(TextDisplay.of(text.isBlank() ? "-# 효과 없음" : NoticeListener.cut(text, IdentityListener.TEXT_LIMIT)));
        if (gift.wiki()) {
            children.add(TextDisplay.of(WIKI_CREDIT));
        }
        children.addAll(IdentityListener.rows(buttons(gift, shown)));
        int color = gift.sin() == null ? IdentityListener.DEFAULT_COLOR
                : IdentityListener.SIN_COLORS.getOrDefault(gift.sin(), IdentityListener.DEFAULT_COLOR);
        return Container.of(children).withAccentColor(color);
    }

    // 강화판이 없는 기프트는 버튼 없이
    static List<Button> buttons(Gift gift, int level) {
        if (gift.texts().size() < 2) {
            return List.of();
        }
        List<Button> buttons = new ArrayList<>();
        for (int i = 0; i < gift.texts().size(); i++) {
            buttons.add(Button.secondary(COMMAND.getName() + ":" + gift.id() + ":" + i, LEVELS.get(i)).withDisabled(i == level));
        }
        return buttons;
    }

    static String grade(Gift gift) {
        List<String> parts = new ArrayList<>();
        if (gift.sin() != null) {
            parts.add(gift.sin());
        }
        if (gift.tier() != null) {
            parts.add("등급 " + gift.tier());
        }
        if (gift.keyword() != null) {
            parts.add(gift.keyword());
        }
        if (gift.cost() != null) {
            parts.add("가격 " + gift.cost());
        }
        return parts.isEmpty() ? null : String.join(" · ", parts);
    }

    // 자동완성 후보를 고르면 값이 기프트 ID, 직접 친 글자면 첫 일치
    static Gift find(List<Gift> gifts, String query) {
        Gift byId = byId(gifts, query);
        return byId != null ? byId : search(gifts, query).stream().findFirst().orElse(null);
    }

    static Gift byId(List<Gift> gifts, String id) {
        return gifts.stream().filter(gift -> String.valueOf(gift.id()).equals(id)).findFirst().orElse(null);
    }

    static List<Gift> search(List<Gift> gifts, String query) {
        String needle = compact(query);
        return gifts.stream()
                .filter(gift -> compact(gift.name()).contains(needle))
                .limit(OptionData.MAX_CHOICES)
                .toList();
    }

    private static String compact(String text) {
        return text.replaceAll("\\s", "").toLowerCase(Locale.ROOT);
    }
}
