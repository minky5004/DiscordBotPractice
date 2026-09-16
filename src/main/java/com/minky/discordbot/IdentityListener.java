package com.minky.discordbot;

import com.minky.discordbot.IdentityCatalog.Identity;
import com.minky.discordbot.IdentityCatalog.Passive;
import com.minky.discordbot.IdentityCatalog.Skill;
import com.minky.discordbot.IdentityCatalog.SkillStats;
import com.minky.discordbot.IdentityCatalog.Stats;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
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
import java.util.Map;
import java.util.stream.Collectors;

// 림버스 컴퍼니 인격 조회
public class IdentityListener extends ListenerAdapter {

    // 위키 수치는 CC BY-SA
    private static final String WIKI_CREDIT = "-# 수치: Limbus Company Wiki (wiki.gg)";

    private static final String NAME = "이름";
    private static final String OPEN = "공개";

    // 한 액션 행에 들어가는 버튼 수
    private static final int ROW_SIZE = 5;

    // 컨테이너 색 띠 · 스킬의 죄악을 그대로 쓴다
    private static final Map<String, Integer> SIN_COLORS = Map.of(
            "분노", 0xDC3545, "색욕", 0xE8622C, "나태", 0xE0B032, "탐식", 0x4CA64C,
            "우울", 0x2F8F9C, "오만", 0x3A6FD8, "질투", 0x8B4FD8);

    private static final int DEFAULT_COLOR = 0x5A5A66;

    // 한 메시지의 컴포넌트 텍스트 합계가 4000자 · 한 덩이가 그 절반을 넘지 않게 자른다
    private static final int TEXT_LIMIT = 2000;

    // 버튼은 라벨만큼 넓어진다 · 한 행에 다섯이 들어가게 이름을 자른다
    private static final int LABEL_WIDTH = 14;

    static final SlashCommandData COMMAND = Commands.slash("인격", "림버스 컴퍼니 인격의 스킬 · 패시브 조회")
            .addOptions(
                    // 칭호가 길고 한 · 영이 섞여 자동완성 없이는 쓰이지 않는다
                    new OptionData(OptionType.STRING, NAME, "인격 칭호 또는 수감자 이름", true, true),
                    new OptionData(OptionType.BOOLEAN, OPEN, "채널에 공개 · 기본 나만 보기", false));

    private final IdentityCatalog catalog;

    IdentityListener(IdentityCatalog catalog) {
        this.catalog = catalog;
    }

    @Override
    public void onCommandAutoCompleteInteraction(CommandAutoCompleteInteractionEvent event) {
        if (!COMMAND.getName().equals(event.getName())) {
            return;
        }
        event.replyChoices(search(catalog.identities(), event.getFocusedOption().getValue()).stream()
                .map(identity -> new Command.Choice(label(identity), String.valueOf(identity.id())))
                .toList()).queue();
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!COMMAND.getName().equals(event.getName())) {
            return;
        }
        List<Identity> identities = catalog.identities();
        Identity identity = find(identities, event.getOption(NAME).getAsString());
        if (identity == null) {
            // 기동 직후엔 목록이 아직 비어 있다
            event.reply(identities.isEmpty() ? "인격 목록을 아직 읽지 못했습니다 · 잠시 뒤 다시" : "찾지 못한 인격 · 자동완성 후보에서 선택")
                    .setEphemeral(true).queue();
            return;
        }
        // 컨테이너는 Components V2 라 content · embeds 와 함께 쓸 수 없다
        event.replyComponents(container(identity, 0))
                .useComponentsV2()
                .setEphemeral(!event.getOption(OPEN, false, OptionMapping::getAsBoolean))
                .queue();
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String[] parts = event.getComponentId().split(":", 3);
        if (parts.length != 3 || !COMMAND.getName().equals(parts[0])) {
            return;
        }
        Identity identity = find(catalog.identities(), parts[1]);
        if (identity == null) {
            // 24시간마다 다시 읽으므로 오래된 메시지의 인격이 목록에서 빠질 수 있다
            event.reply("인격 목록에 없는 항목 · 명령을 다시 실행").setEphemeral(true).queue();
            return;
        }
        // 편집도 V1 이 기본이라 플래그를 다시 준다 — 없으면 컨테이너가 거부되고 상호작용이 미응답으로 남는다
        event.editComponents(container(identity, Integer.parseInt(parts[2]))).useComponentsV2().queue();
    }

    // 페이지 0 은 번호 붙은 목록 · 1부터는 그 번호의 스킬 또는 패시브 하나
    static Container container(Identity identity, int page) {
        List<Entry> entries = entries(identity);
        List<TextDisplay> heading = new ArrayList<>();
        List<ContainerChildComponent> body = new ArrayList<>();
        if (page == 0) {
            heading.add(TextDisplay.of("## " + title(identity)));
            String resists = resists(identity.stats());
            if (resists != null) {
                heading.add(TextDisplay.of(resists));
            }
            body.add(TextDisplay.of(list(entries)));
        } else if (page <= entries.size()) {
            // 상세에서는 그 스킬이 주인공 · 어느 인격인지는 썸네일과 색 띠가 이미 말한다
            Entry entry = entries.get(page - 1);
            heading.add(TextDisplay.of("### " + entry.name()));
            heading.add(TextDisplay.of("-# " + title(identity)));
            heading.add(TextDisplay.of(entry.stats()));
            body.add(TextDisplay.of(entry.text().isBlank() ? "-# 효과 없음" : body(entry.text())));
        }

        List<ContainerChildComponent> children = new ArrayList<>();
        // 썸네일은 섹션의 곁들임으로만 붙는다 · 이미지가 없으면 머리를 그대로 쌓는다
        String thumbnail = thumbnail(identity, page);
        if (thumbnail == null) {
            children.addAll(heading);
        } else {
            String alt = page > 0 && page <= entries.size() ? entries.get(page - 1).name() : label(identity);
            children.add(Section.of(Thumbnail.fromUrl(thumbnail).withDescription(alt), heading));
        }
        children.add(Separator.createDivider(Separator.Spacing.SMALL));
        children.addAll(body);
        if (identity.stats() != null) {
            children.add(TextDisplay.of(WIKI_CREDIT));
        }
        children.addAll(rows(identity, page));
        return Container.of(children).withAccentColor(color(identity, page));
    }

    // 스킬 페이지는 그 스킬의 아이콘 · 목록과 패시브는 인격 일러스트
    private static String thumbnail(Identity identity, int page) {
        List<Skill> skills = identity.skills();
        if (page > 0 && page <= skills.size()) {
            SkillStats stats = skills.get(page - 1).stats();
            if (stats != null && stats.icon() != null) {
                return stats.icon();
            }
        }
        return identity.image();
    }

    // 훑는 자리라 이름이 주 · 수치는 작은 글씨로 딸린다
    private static String list(List<Entry> entries) {
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            lines.add("**`" + (i + 1) + "`  " + entries.get(i).name() + "**");
            lines.add("-# " + entries.get(i).stats());
        }
        return NoticeListener.cut(String.join("\n", lines), TEXT_LIMIT);
    }

    // 코인별 효과는 볼드로 떼어내고 나머지 줄은 원문 그대로
    private static String body(String text) {
        List<String> lines = new ArrayList<>();
        for (String line : text.split("\n")) {
            lines.add(line.replaceFirst("^코인(\\d+) ", "**코인 $1** "));
        }
        return NoticeListener.cut(String.join("\n", lines), TEXT_LIMIT);
    }

    // 0 은 목록 · 1부터가 스킬과 패시브 · 현재 페이지는 누를 수 없다
    static List<Button> buttons(Identity identity, int page) {
        List<Entry> entries = entries(identity);
        // 목록 버튼이 한 자리를 쓴다
        int count = Math.min(entries.size(), ROW_SIZE * ROW_SIZE - 1);
        if (count < 2) {
            return List.of();
        }
        String id = COMMAND.getName() + ":" + identity.id() + ":";
        List<Button> buttons = new ArrayList<>();
        buttons.add(Button.secondary(id + 0, "목록").withDisabled(page == 0));
        for (int i = 1; i <= count; i++) {
            // 번호만 있으면 무엇이 있는지 알려면 목록으로 돌아가야 한다
            buttons.add(Button.secondary(id + i, NoticeListener.cut(label(i, entries.get(i - 1)), LABEL_WIDTH))
                    .withDisabled(page == i));
        }
        return buttons;
    }

    // 패시브는 이름 앞의 종류까지 버튼에 넣을 자리가 없다
    private static String label(int number, Entry entry) {
        String name = entry.name();
        int mark = name.indexOf(" · ");
        return number + " " + (name.startsWith("패시브") || name.startsWith("서포트 패시브") ? name.substring(mark + 3) : name);
    }

    private static List<ActionRow> rows(Identity identity, int page) {
        List<Button> buttons = buttons(identity, page);
        List<ActionRow> rows = new ArrayList<>();
        for (int i = 0; i < buttons.size(); i += ROW_SIZE) {
            rows.add(ActionRow.of(buttons.subList(i, Math.min(buttons.size(), i + ROW_SIZE))));
        }
        return rows;
    }

    // 상세는 그 스킬의 죄악 · 목록은 가장 많이 쓰인 죄악
    private static int color(Identity identity, int page) {
        List<Skill> skills = identity.skills();
        if (page > 0 && page <= skills.size()) {
            return sinColor(skills.get(page - 1));
        }
        return skills.stream()
                .collect(Collectors.groupingBy(IdentityListener::sinColor, Collectors.counting()))
                .entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(DEFAULT_COLOR);
    }

    private static int sinColor(Skill skill) {
        SkillStats stats = skill.stats();
        return stats == null || stats.sin() == null ? DEFAULT_COLOR : SIN_COLORS.getOrDefault(stats.sin(), DEFAULT_COLOR);
    }

    // 자동완성 후보를 고르면 값이 인격 ID, 직접 친 글자면 첫 일치
    static Identity find(List<Identity> identities, String query) {
        return identities.stream()
                .filter(identity -> String.valueOf(identity.id()).equals(query))
                .findFirst()
                .or(() -> search(identities, query).stream().findFirst())
                .orElse(null);
    }

    // 스킬 다음 패시브 · 목록에 붙는 번호와 페이지 번호가 이 순서다
    private record Entry(String name, String stats, String text) {
    }

    // 맵으로 모으면 제목이 같은 스킬이 하나로 합쳐진다
    private static List<Entry> entries(Identity identity) {
        List<Entry> entries = new ArrayList<>();
        for (Skill skill : identity.skills()) {
            entries.add(new Entry(skill.name(), String.join(" · ", skillStats(skill)), skill.text()));
        }
        for (Passive passive : identity.passives()) {
            String name = (passive.support() ? "서포트 패시브" : "패시브") + " · " + passive.name();
            entries.add(new Entry(name, passive.condition() == null ? "조건 없음" : passive.condition(), passive.text()));
        }
        return entries;
    }

    static List<Identity> search(List<Identity> identities, String query) {
        String needle = compact(query);
        return identities.stream()
                .filter(identity -> compact(identity.title() + identity.sinner()).contains(needle))
                .limit(OptionData.MAX_CHOICES)
                .toList();
    }

    static String label(Identity identity) {
        return NoticeListener.cut("[" + identity.title() + "] " + identity.sinner(), OptionData.MAX_CHOICE_NAME_LENGTH);
    }

    private static String title(Identity identity) {
        String title = "[" + identity.title() + "] " + identity.sinner();
        Stats stats = identity.stats();
        if (stats != null && stats.rarity() != null && stats.rarity() > 0) {
            title += " · " + "★".repeat(stats.rarity());
        }
        if (stats != null && stats.season() != null) {
            title += " · 시즌 " + stats.season();
        }
        return title;
    }

    private static String resists(Stats stats) {
        if (stats == null) {
            return null;
        }
        List<String> resists = new ArrayList<>();
        addResist(resists, "참격", stats.slash());
        addResist(resists, "관통", stats.pierce());
        addResist(resists, "타격", stats.blunt());
        return resists.isEmpty() ? null : "내성  " + String.join(" · ", resists);
    }

    private static void addResist(List<String> resists, String type, Double multiplier) {
        if (multiplier != null) {
            resists.add(type + " ×" + multiplier);
        }
    }

    private static List<String> skillStats(Skill skill) {
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
        return parts.isEmpty() ? List.of("수치 없음") : parts;
    }

    private static String compact(String text) {
        return text.replaceAll("\\s", "").toLowerCase(Locale.ROOT);
    }
}
