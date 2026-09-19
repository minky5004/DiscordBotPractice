package com.minky.discordbot;

import com.minky.discordbot.EgoCatalog.Ego;
import com.minky.discordbot.EgoCatalog.EgoSkill;
import com.minky.discordbot.IdentityCatalog.Passive;
import com.minky.discordbot.IdentityCatalog.SkillStats;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.container.ContainerChildComponent;
import net.dv8tion.jda.api.components.section.Section;
import net.dv8tion.jda.api.components.separator.Separator;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.components.thumbnail.Thumbnail;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

// 림버스 컴퍼니 E.G.O 조회 · 각성 · 침식 · 패시브가 한 화면
public class EgoListener extends ListenerAdapter {

    private static final String WIKI_CREDIT = "-# 수치: Limbus Company Wiki (wiki.gg)";

    private static final String NAME = "이름";
    private static final String OPEN = "공개";

    // 한 메시지의 컴포넌트 텍스트 합계가 4000자 · 머리 · 출처 줄 몫을 남긴 본문 예산
    static final int BODY_LIMIT = 3500;

    static final SlashCommandData COMMAND = Commands.slash("에고", "림버스 컴퍼니 E.G.O 의 스킬 · 패시브 조회")
            .addOptions(
                    new OptionData(OptionType.STRING, NAME, "E.G.O 또는 수감자 이름", true, true),
                    new OptionData(OptionType.BOOLEAN, OPEN, "채널에 공개 · 기본 나만 보기", false));

    private final IdentityCatalog catalog;

    EgoListener(IdentityCatalog catalog) {
        this.catalog = catalog;
    }

    @Override
    public void onCommandAutoCompleteInteraction(CommandAutoCompleteInteractionEvent event) {
        if (!COMMAND.getName().equals(event.getName())) {
            return;
        }
        event.replyChoices(search(catalog.egos(), event.getFocusedOption().getValue()).stream()
                .map(ego -> new Command.Choice(label(ego), String.valueOf(ego.id())))
                .toList()).queue();
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!COMMAND.getName().equals(event.getName())) {
            return;
        }
        List<Ego> egos = catalog.egos();
        Ego ego = find(egos, event.getOption(NAME).getAsString());
        if (ego == null) {
            // 기동 직후엔 목록이 아직 비어 있다
            event.reply(egos.isEmpty() ? "E.G.O 목록을 아직 읽지 못했습니다 · 잠시 뒤 다시" : "찾지 못한 E.G.O · 자동완성 후보에서 선택")
                    .setEphemeral(true).queue();
            return;
        }
        event.replyComponents(container(ego))
                .useComponentsV2()
                .setEphemeral(!event.getOption(OPEN, false, OptionMapping::getAsBoolean))
                .queue();
    }

    static Container container(Ego ego) {
        List<TextDisplay> heading = new ArrayList<>();
        heading.add(TextDisplay.of("## " + ego.name()));
        heading.add(TextDisplay.of("-# " + ego.sinner() + (ego.risk() == null ? "" : " · " + ego.risk())));
        if (!ego.costs().isEmpty()) {
            heading.add(TextDisplay.of("자원  " + join(ego.costs(), cost -> "×" + cost)));
        }

        List<ContainerChildComponent> children = new ArrayList<>();
        // 썸네일은 섹션의 곁들임으로만 붙는다 · 섹션 내용은 3개까지라 내성은 밖으로
        if (ego.image() == null) {
            children.addAll(heading);
        } else {
            children.add(Section.of(Thumbnail.fromUrl(ego.image()).withDescription(label(ego)), heading));
        }
        String resists = resists(ego.resists());
        if (resists != null) {
            children.add(TextDisplay.of(resists));
        }
        children.add(Separator.createDivider(Separator.Spacing.SMALL));
        List<String> blocks = new ArrayList<>();
        for (EgoSkill skill : ego.skills()) {
            blocks.add(block((skill.corrosion() ? "침식" : "각성") + " · " + skill.name(),
                    "-# " + String.join(" · ", stats(skill)), skill.text()));
        }
        for (Passive passive : ego.passives()) {
            blocks.add(block("패시브 · " + passive.name(), null, passive.text()));
        }
        // 긴 스킬 하나(1800자대)는 그대로 · 합계가 넘칠 때만 덩어리마다 같은 몫으로 자른다
        int total = blocks.stream().mapToInt(String::length).sum();
        int share = total <= BODY_LIMIT ? Integer.MAX_VALUE : BODY_LIMIT / blocks.size();
        blocks.forEach(block -> children.add(TextDisplay.of(NoticeListener.cut(block, share))));
        if (ego.wiki()) {
            children.add(TextDisplay.of(WIKI_CREDIT));
        }
        return Container.of(children).withAccentColor(color(ego));
    }

    private static String block(String title, String stats, String text) {
        List<String> lines = new ArrayList<>();
        lines.add("### " + title);
        if (stats != null) {
            lines.add(stats);
        }
        if (text.isBlank()) {
            lines.add("-# 효과 없음");
        }
        for (String line : text.split("\n")) {
            if (!line.isBlank()) {
                lines.add(line.replaceFirst("^코인(\\d+) ", "**코인 $1** "));
            }
        }
        return String.join("\n", lines);
    }

    private static List<String> stats(EgoSkill skill) {
        List<String> parts = new ArrayList<>();
        SkillStats stats = skill.stats();
        if (stats != null) {
            if (stats.sin() != null) {
                parts.add(stats.sin());
            }
            if (stats.type() != null) {
                parts.add(stats.type());
            }
            if (stats.power() != null) {
                parts.add("위력 " + stats.power());
            }
            if (stats.coinPower() != null) {
                parts.add("코인 " + stats.coinPower() + (stats.coins() == null ? "" : " × " + stats.coins()));
            }
        }
        if (parts.isEmpty()) {
            parts.add("수치 없음");
        }
        if (skill.sanity() != null) {
            parts.add("정신력 " + skill.sanity());
        }
        return parts;
    }

    // 보통(×1.0)은 7칸 중 대부분이라 빼고 약점 · 내성만
    private static String resists(Map<String, Double> resists) {
        Map<String, Double> notable = resists.entrySet().stream()
                .filter(entry -> entry.getValue() != 1.0)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));
        return notable.isEmpty() ? null : "내성  " + join(notable, multiplier -> "×" + multiplier);
    }

    private static <V> String join(Map<String, V> values, Function<V, String> format) {
        return values.entrySet().stream()
                .map(entry -> entry.getKey() + " " + format.apply(entry.getValue()))
                .collect(Collectors.joining(" · "));
    }

    private static int color(Ego ego) {
        return ego.skills().stream()
                .filter(skill -> !skill.corrosion() && skill.stats() != null && skill.stats().sin() != null)
                .map(skill -> IdentityListener.SIN_COLORS.getOrDefault(skill.stats().sin(), IdentityListener.DEFAULT_COLOR))
                .findFirst()
                .orElse(IdentityListener.DEFAULT_COLOR);
    }

    // 자동완성 후보를 고르면 값이 E.G.O ID, 직접 친 글자면 첫 일치
    static Ego find(List<Ego> egos, String query) {
        return egos.stream()
                .filter(ego -> String.valueOf(ego.id()).equals(query))
                .findFirst()
                .orElseGet(() -> search(egos, query).stream().findFirst().orElse(null));
    }

    static List<Ego> search(List<Ego> egos, String query) {
        String needle = compact(query);
        return egos.stream()
                .filter(ego -> compact(ego.name() + ego.sinner()).contains(needle))
                .limit(OptionData.MAX_CHOICES)
                .toList();
    }

    static String label(Ego ego) {
        return NoticeListener.cut(ego.name() + " · " + ego.sinner(), OptionData.MAX_CHOICE_NAME_LENGTH);
    }

    private static String compact(String text) {
        return text.replaceAll("\\s", "").toLowerCase(Locale.ROOT);
    }
}
