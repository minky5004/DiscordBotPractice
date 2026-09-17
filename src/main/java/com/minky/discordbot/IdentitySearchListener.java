package com.minky.discordbot;

import com.minky.discordbot.IdentityCatalog.Identity;
import com.minky.discordbot.IdentityCatalog.Skill;
import com.minky.discordbot.IdentityCatalog.SkillStats;
import com.minky.discordbot.IdentityCatalog.Stats;
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

// 조건으로 인격 목록 좁히기 · 결과 버튼은 /인격 상세로 그대로 이어진다
public class IdentitySearchListener extends ListenerAdapter {

    private static final String SIN = "죄악";
    private static final String TYPE = "유형";
    private static final String SINNER = "수감자";
    private static final String RARITY = "등급";
    private static final String SEASON = "시즌";
    private static final String OPEN = "공개";

    // 게임 표기 순서
    private static final List<String> SINS = List.of("분노", "색욕", "나태", "탐식", "우울", "오만", "질투");

    // 방어 · 회피는 상대의 약점을 묻는 자리가 아니라 뺀다
    private static final List<String> TYPES = List.of("참격", "관통", "타격");

    // 수감자 열둘은 고정 · 게임 텍스트의 이름과 글자가 같아야 걸린다
    private static final List<String> SINNERS = List.of("이상", "파우스트", "돈키호테", "료슈", "뫼르소", "홍루",
            "히스클리프", "이스마엘", "로쟈", "싱클레어", "오티스", "그레고르");

    // 결과 버튼 25개가 한 메시지의 액션 행 다섯 줄을 채운다
    private static final int MAX_RESULTS = 25;

    // 상한이 없으면 getAsInt() 가 ArithmeticException 으로 터진다 — reply() 전이라 상호작용이 미응답으로 남는다
    private static final int MAX_RARITY = 3;

    // 시즌 구분 없는 초기 인격이 위키 표기로 0 이라 하한도 0 (실측 · 187건 중 시즌 0 이 최다)
    private static final int MIN_SEASON = 0;
    private static final int MAX_SEASON = 99;

    static final SlashCommandData COMMAND = Commands.slash("인격검색", "죄악 · 유형 · 수감자 · 등급 · 시즌으로 인격 목록 좁히기")
            .addOptions(
                    // 죄악과 유형은 스킬 하나가 둘 다 만족해야 걸린다 — 상성은 그 단위로 본다
                    choice(SIN, "스킬의 죄악", SINS),
                    choice(TYPE, "스킬의 공격 유형", TYPES),
                    choice(SINNER, "수감자", SINNERS),
                    new OptionData(OptionType.INTEGER, RARITY, "등급 ★", false).setRequiredRange(1, MAX_RARITY),
                    new OptionData(OptionType.INTEGER, SEASON, "시즌", false).setRequiredRange(MIN_SEASON, MAX_SEASON),
                    new OptionData(OptionType.BOOLEAN, OPEN, "채널에 공개 · 기본 나만 보기", false));

    private final IdentityCatalog catalog;

    IdentitySearchListener(IdentityCatalog catalog) {
        this.catalog = catalog;
    }

    // 주지 않은 조건은 null · 전부 null 이면 전체 목록
    record Filter(String sin, String type, String sinner, Integer rarity, Integer season) {
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!COMMAND.getName().equals(event.getName())) {
            return;
        }
        List<Identity> identities = catalog.identities();
        if (identities.isEmpty()) {
            // 기동 직후엔 목록이 아직 비어 있다
            event.reply("인격 목록을 아직 읽지 못했습니다 · 잠시 뒤 다시").setEphemeral(true).queue();
            return;
        }
        Filter filter = new Filter(
                event.getOption(SIN, OptionMapping::getAsString),
                event.getOption(TYPE, OptionMapping::getAsString),
                event.getOption(SINNER, OptionMapping::getAsString),
                event.getOption(RARITY, OptionMapping::getAsInt),
                event.getOption(SEASON, OptionMapping::getAsInt));
        // 컨테이너는 Components V2 라 content · embeds 와 함께 쓸 수 없다
        event.replyComponents(container(search(identities, filter), filter))
                .useComponentsV2()
                .setEphemeral(!event.getOption(OPEN, false, OptionMapping::getAsBoolean))
                .queue();
    }

    // 카탈로그 순서는 게임 ID 순이라 수감자별로 뭉쳐 있다 — 그대로 앞 25건을 자르면 넓은 검색이 앞 두 수감자만 내놓는다
    private static final Comparator<Identity> ORDER = Comparator
            .comparing((Identity identity) -> identity.stats() == null ? null : identity.stats().rarity(),
                    Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(Identity::id);

    static List<Identity> search(List<Identity> identities, Filter filter) {
        return identities.stream().filter(identity -> matches(identity, filter)).sorted(ORDER).toList();
    }

    // 수감자 · 등급 · 시즌은 인격 단위 · 죄악 · 유형은 스킬 단위
    static boolean matches(Identity identity, Filter filter) {
        if (filter.sinner() != null && !filter.sinner().equals(identity.sinner())) {
            return false;
        }
        Stats stats = identity.stats();
        if (filter.rarity() != null && (stats == null || !filter.rarity().equals(stats.rarity()))) {
            return false;
        }
        if (filter.season() != null && (stats == null || !filter.season().equals(stats.season()))) {
            return false;
        }
        if (filter.sin() == null && filter.type() == null) {
            return true;
        }
        // 분노 스킬 따로 · 관통 스킬 따로인 인격을 "분노 관통" 으로 내주면 상성 표로 못 쓴다
        return identity.skills().stream().anyMatch(skill -> skillMatches(skill, filter));
    }

    private static boolean skillMatches(Skill skill, Filter filter) {
        SkillStats stats = skill.stats();
        if (stats == null) {
            return false;
        }
        return (filter.sin() == null || filter.sin().equals(stats.sin()))
                && (filter.type() == null || filter.type().equals(stats.type()));
    }

    static Container container(List<Identity> results, Filter filter) {
        List<ContainerChildComponent> children = new ArrayList<>();
        children.add(TextDisplay.of("## 인격 검색"));
        children.add(TextDisplay.of("-# " + describe(filter)));
        children.add(Separator.createDivider(Separator.Spacing.SMALL));
        if (results.isEmpty()) {
            // 디스코드는 빈 TextDisplay 를 거부한다
            children.add(TextDisplay.of("조건에 맞는 인격 없음"));
            return Container.of(children).withAccentColor(color(filter));
        }
        List<Identity> shown = results.subList(0, Math.min(results.size(), MAX_RESULTS));
        children.add(TextDisplay.of(list(shown)));
        if (results.size() > shown.size()) {
            children.add(TextDisplay.of(
                    "-# " + results.size() + "건 중 등급 높은 앞 " + shown.size() + "건 · 수감자 · 시즌으로 더 좁히기"));
        }
        children.addAll(IdentityListener.rows(buttons(shown)));
        return Container.of(children).withAccentColor(color(filter));
    }

    static String describe(Filter filter) {
        List<String> parts = new ArrayList<>();
        if (filter.sin() != null) {
            parts.add(filter.sin());
        }
        if (filter.type() != null) {
            parts.add(filter.type());
        }
        if (filter.sinner() != null) {
            parts.add(filter.sinner());
        }
        if (filter.rarity() != null) {
            parts.add("★".repeat(filter.rarity()));
        }
        if (filter.season() != null) {
            parts.add("시즌 " + filter.season());
        }
        return parts.isEmpty() ? "조건 없음 · 전체" : String.join(" · ", parts);
    }

    // 훑는 자리라 이름이 주 · 등급과 시즌은 작은 글씨로 딸린다
    private static String list(List<Identity> shown) {
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < shown.size(); i++) {
            lines.add("**`" + (i + 1) + "`  " + IdentityListener.label(shown.get(i)) + "**");
            lines.add("-# " + grade(shown.get(i)));
        }
        return NoticeListener.cut(String.join("\n", lines), IdentityListener.TEXT_LIMIT);
    }

    private static String grade(Identity identity) {
        Stats stats = identity.stats();
        if (stats == null) {
            return "수치 없음";
        }
        List<String> parts = new ArrayList<>();
        if (stats.rarity() != null && stats.rarity() > 0) {
            parts.add("★".repeat(stats.rarity()));
        }
        if (stats.season() != null) {
            parts.add("시즌 " + stats.season());
        }
        return parts.isEmpty() ? "수치 없음" : String.join(" · ", parts);
    }

    // /인격 상세의 customId 를 그대로 발급한다 — 누르면 IdentityListener 가 받아 그 메시지를 인격 화면으로 바꾼다
    static List<Button> buttons(List<Identity> shown) {
        List<Button> buttons = new ArrayList<>();
        for (int i = 0; i < shown.size(); i++) {
            Identity identity = shown.get(i);
            buttons.add(Button.secondary(IdentityListener.COMMAND.getName() + ":" + identity.id() + ":0",
                    NoticeListener.cut((i + 1) + " " + identity.title(), IdentityListener.LABEL_WIDTH)));
        }
        return buttons;
    }

    // 죄악을 준 검색만 색이 선다 · 나머지는 인격 화면의 기본색과 같게
    private static int color(Filter filter) {
        return filter.sin() == null ? IdentityListener.DEFAULT_COLOR
                : IdentityListener.SIN_COLORS.getOrDefault(filter.sin(), IdentityListener.DEFAULT_COLOR);
    }

    private static OptionData choice(String name, String description, List<String> values) {
        OptionData option = new OptionData(OptionType.STRING, name, description, false);
        values.forEach(value -> option.addChoice(value, value));
        return option;
    }
}
