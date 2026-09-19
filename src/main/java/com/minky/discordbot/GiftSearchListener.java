package com.minky.discordbot;

import com.minky.discordbot.GiftCatalog.Gift;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.container.ContainerChildComponent;
import net.dv8tion.jda.api.components.separator.Separator;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

// 조건으로 기프트 목록 좁히기 · 결과 버튼은 /기프트 화면으로 그대로 이어진다
public class GiftSearchListener extends ListenerAdapter {

    private static final String KEYWORD = "키워드";
    private static final String TIER = "등급";
    private static final String SIN = "죄악";
    private static final String OPEN = "공개";

    // 위키 기프트 분류 · 게임 화면의 키워드 탭과 같은 열
    private static final List<String> KEYWORDS = List.of("화상", "출혈", "진동", "파열", "침잠", "호흡", "충전", "참격", "관통", "타격");

    // 낮은 것부터 · EX 는 특수 기프트라 맨 위
    private static final List<String> TIERS = List.of("I", "II", "III", "IV", "V", "EX");

    private static final List<String> SINS = List.of("분노", "색욕", "나태", "탐식", "우울", "오만", "질투");

    // 결과 버튼 25개가 한 메시지의 액션 행 다섯 줄을 채운다
    private static final int MAX_RESULTS = 25;

    static final SlashCommandData COMMAND = Commands.slash("기프트검색", "키워드 · 등급 · 죄악으로 기프트 목록 좁히기")
            .addOptions(
                    choice(KEYWORD, "기프트 키워드", KEYWORDS),
                    choice(TIER, "기프트 등급", TIERS),
                    choice(SIN, "기프트 죄악", SINS),
                    new OptionData(OptionType.BOOLEAN, OPEN, "채널에 공개 · 기본 나만 보기", false));

    private final IdentityCatalog catalog;

    GiftSearchListener(IdentityCatalog catalog) {
        this.catalog = catalog;
    }

    // 주지 않은 조건은 null · 전부 null 이면 전체 목록
    record Filter(String keyword, String tier, String sin) {
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!COMMAND.getName().equals(event.getName())) {
            return;
        }
        List<Gift> gifts = catalog.gifts();
        if (gifts.isEmpty()) {
            event.reply("기프트 목록을 아직 읽지 못했습니다 · 잠시 뒤 다시").setEphemeral(true).queue();
            return;
        }
        Filter filter = new Filter(
                event.getOption(KEYWORD, OptionMapping::getAsString),
                event.getOption(TIER, OptionMapping::getAsString),
                event.getOption(SIN, OptionMapping::getAsString));
        event.replyComponents(container(search(gifts, filter), filter))
                .useComponentsV2()
                .setEphemeral(!event.getOption(OPEN, false, OptionMapping::getAsBoolean))
                .queue();
    }

    // 게임 ID 순은 거울 던전 시즌별로 뭉쳐 있다 · 높은 등급 먼저
    private static final Comparator<Gift> ORDER = Comparator
            .comparing((Gift gift) -> TIERS.indexOf(gift.tier()), Comparator.reverseOrder())
            .thenComparing(Gift::id);

    static List<Gift> search(List<Gift> gifts, Filter filter) {
        return gifts.stream()
                .filter(gift -> filter.keyword() == null || filter.keyword().equals(gift.keyword()))
                .filter(gift -> filter.tier() == null || filter.tier().equals(gift.tier()))
                .filter(gift -> filter.sin() == null || filter.sin().equals(gift.sin()))
                .sorted(ORDER)
                .toList();
    }

    static Container container(List<Gift> results, Filter filter) {
        List<ContainerChildComponent> children = new ArrayList<>();
        children.add(TextDisplay.of("## 기프트 검색"));
        children.add(TextDisplay.of("-# " + describe(filter)));
        children.add(Separator.createDivider(Separator.Spacing.SMALL));
        int color = filter.sin() == null ? IdentityListener.DEFAULT_COLOR
                : IdentityListener.SIN_COLORS.getOrDefault(filter.sin(), IdentityListener.DEFAULT_COLOR);
        if (results.isEmpty()) {
            // 디스코드는 빈 TextDisplay 를 거부한다
            children.add(TextDisplay.of("조건에 맞는 기프트 없음"));
            return Container.of(children).withAccentColor(color);
        }
        List<Gift> shown = results.subList(0, Math.min(results.size(), MAX_RESULTS));
        children.add(TextDisplay.of(list(shown)));
        if (results.size() > shown.size()) {
            children.add(TextDisplay.of("-# " + results.size() + "건 중 등급 높은 앞 " + shown.size() + "건 · 조건을 더 주면 좁혀짐"));
        }
        children.addAll(IdentityListener.rows(buttons(shown)));
        return Container.of(children).withAccentColor(color);
    }

    static String describe(Filter filter) {
        List<String> parts = new ArrayList<>();
        if (filter.keyword() != null) {
            parts.add(filter.keyword());
        }
        if (filter.tier() != null) {
            parts.add("등급 " + filter.tier());
        }
        if (filter.sin() != null) {
            parts.add(filter.sin());
        }
        return parts.isEmpty() ? "조건 없음 · 전체" : String.join(" · ", parts);
    }

    private static String list(List<Gift> shown) {
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < shown.size(); i++) {
            String grade = GiftListener.grade(shown.get(i));
            lines.add("**`" + (i + 1) + "`  " + shown.get(i).name() + "**");
            lines.add("-# " + (grade == null ? "수치 없음" : grade));
        }
        return NoticeListener.cut(String.join("\n", lines), IdentityListener.TEXT_LIMIT);
    }

    // /기프트 의 customId 를 그대로 발급한다 — 누르면 GiftListener 가 받아 그 메시지를 기프트 화면으로 바꾼다
    static List<Button> buttons(List<Gift> shown) {
        List<Button> buttons = new ArrayList<>();
        for (int i = 0; i < shown.size(); i++) {
            buttons.add(Button.secondary(GiftListener.COMMAND.getName() + ":" + shown.get(i).id() + ":0",
                    NoticeListener.cut((i + 1) + " " + shown.get(i).name(), IdentityListener.LABEL_WIDTH)));
        }
        return buttons;
    }

    private static OptionData choice(String name, String description, List<String> values) {
        OptionData option = new OptionData(OptionType.STRING, name, description, false);
        values.forEach(value -> option.addChoice(value, value));
        return option;
    }
}
