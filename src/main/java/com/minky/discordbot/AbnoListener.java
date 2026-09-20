package com.minky.discordbot;

import com.minky.discordbot.AbnoCatalog.Abno;
import com.minky.discordbot.AbnoCatalog.Log;
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

// 환상체 도감 조회 · 관찰 로그는 버튼 페이지로
public class AbnoListener extends ListenerAdapter {

    private static final String WIKI_CREDIT = "-# 정보: Limbus Company Wiki (wiki.gg)";

    private static final String NAME = "이름";
    private static final String OPEN = "공개";

    // 위험 등급의 게임 색 · 뒤에 붙는 숫자는 떼고 본다
    private static final Map<String, Integer> RISK_COLORS = Map.of(
            "ZAYIN", 0x4CA64C, "TETH", 0xE0B032, "HE", 0x3A6FD8, "WAW", 0x8B4FD8, "ALEPH", 0xDC3545);

    static final SlashCommandData COMMAND = Commands.slash("환상체", "환상체 도감 · 위험 등급 · 유래 E.G.O · 관찰 로그")
            .addOptions(
                    new OptionData(OptionType.STRING, NAME, "환상체 이름", true, true),
                    new OptionData(OptionType.BOOLEAN, OPEN, "채널에 공개 · 기본 나만 보기", false));

    private final IdentityCatalog catalog;

    AbnoListener(IdentityCatalog catalog) {
        this.catalog = catalog;
    }

    @Override
    public void onCommandAutoCompleteInteraction(CommandAutoCompleteInteractionEvent event) {
        if (!COMMAND.getName().equals(event.getName())) {
            return;
        }
        event.replyChoices(search(catalog.abnos(), event.getFocusedOption().getValue()).stream()
                .map(abno -> new Command.Choice(NoticeListener.cut(abno.name(), OptionData.MAX_CHOICE_NAME_LENGTH), abno.title()))
                .toList()).queue();
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!COMMAND.getName().equals(event.getName())) {
            return;
        }
        List<Abno> abnos = catalog.abnos();
        Abno abno = find(abnos, event.getOption(NAME).getAsString());
        if (abno == null) {
            // 기동 직후엔 목록이 아직 비어 있다
            event.reply(abnos.isEmpty() ? "환상체 목록을 아직 읽지 못했습니다 · 잠시 뒤 다시" : "찾지 못한 환상체 · 자동완성 후보에서 선택")
                    .setEphemeral(true).queue();
            return;
        }
        event.replyComponents(container(abno, 0))
                .useComponentsV2()
                .setEphemeral(!event.getOption(OPEN, false, OptionMapping::getAsBoolean))
                .queue();
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        // 위키 문서 제목이 곧 ID 라 뒤에서 자른다 · 제목에 콜론이 있어도 페이지 번호가 살아남는다
        String id = event.getComponentId();
        int split = id.lastIndexOf(':');
        if (split < 0 || !id.startsWith(COMMAND.getName() + ":")) {
            return;
        }
        // 버튼은 문서 제목으로만 찾는다 · 글자 검색까지 타면 24시간 갱신으로 빠진 환상체가 다른 환상체로 바뀐다
        Abno abno = byTitle(catalog.abnos(), id.substring(COMMAND.getName().length() + 1, split));
        if (abno == null) {
            event.reply("환상체 목록에 없는 항목 · 명령을 다시 실행").setEphemeral(true).queue();
            return;
        }
        // 편집도 V1 이 기본이라 플래그를 다시 준다
        event.editComponents(container(abno, Integer.parseInt(id.substring(split + 1)))).useComponentsV2().queue();
    }

    // 0 은 위키 정보 · 1부터가 관찰 로그
    static Container container(Abno abno, int page) {
        // 범위 밖은 위키 정보로 · 24시간 갱신이 로그를 줄이면 옛 메시지의 버튼이 여기로 온다
        int shown = page > 0 && page <= abno.logs().size() ? page : 0;
        List<TextDisplay> heading = List.of(
                TextDisplay.of("## " + abno.name() + (shown == 0 ? "" : " · 관찰 " + shown)),
                TextDisplay.of("-# " + subtitle(abno)));

        List<ContainerChildComponent> children = new ArrayList<>();
        if (abno.image() == null) {
            children.addAll(heading);
        } else {
            children.add(Section.of(Thumbnail.fromUrl(abno.image()).withDescription(abno.name()), heading));
        }
        children.add(Separator.createDivider(Separator.Spacing.SMALL));
        children.add(TextDisplay.of(shown == 0 ? info(abno)
                : NoticeListener.cut(abno.logs().get(shown - 1).text(), IdentityListener.TEXT_LIMIT)));
        children.add(TextDisplay.of(WIKI_CREDIT));
        children.addAll(IdentityListener.rows(buttons(abno, shown)));
        return Container.of(children).withAccentColor(color(abno.risk()));
    }

    // 제목 아래 한 줄 · 위키 문서 제목이 영어 이름이다
    static String subtitle(Abno abno) {
        List<String> parts = new ArrayList<>();
        parts.add(abno.title());
        if (abno.code() != null) {
            parts.add(abno.code());
        }
        if (abno.risk() != null) {
            parts.add(abno.risk());
        }
        return String.join(" · ", parts);
    }

    static String info(Abno abno) {
        List<String> lines = new ArrayList<>();
        if (abno.ego() != null) {
            lines.add("**유래 E.G.O** " + abno.ego());
        }
        if (!abno.gifts().isEmpty()) {
            lines.add("**E.G.O 기프트** " + String.join(" · ", abno.gifts()));
        }
        if (abno.location() != null) {
            lines.add("**등장** " + abno.location());
        }
        // 디스코드는 빈 TextDisplay 를 거부한다
        return lines.isEmpty() ? "-# 위키에 적힌 정보 없음" : String.join("\n", lines);
    }

    // 관찰 로그가 없는 환상체는 버튼 없이 · 거울 던전 이벤트에서만 만나는 쪽은 도감 항목이 없다
    static List<Button> buttons(Abno abno, int page) {
        if (abno.logs().isEmpty()) {
            return List.of();
        }
        String id = COMMAND.getName() + ":" + abno.title() + ":";
        List<Button> buttons = new ArrayList<>();
        buttons.add(Button.secondary(id + 0, "정보").withDisabled(page == 0));
        for (int i = 1; i <= abno.logs().size(); i++) {
            buttons.add(Button.secondary(id + i, "관찰 " + i).withDisabled(page == i));
        }
        return buttons;
    }

    static int color(String risk) {
        if (risk != null) {
            // TETH-03 처럼 뒤에 내부 번호가 붙는다
            Integer color = RISK_COLORS.get(risk.split("-")[0].toUpperCase(Locale.ROOT));
            if (color != null) {
                return color;
            }
        }
        return IdentityListener.DEFAULT_COLOR;
    }

    // 자동완성 후보를 고르면 값이 위키 문서 제목, 직접 친 글자면 첫 일치
    static Abno find(List<Abno> abnos, String query) {
        Abno byTitle = byTitle(abnos, query);
        return byTitle != null ? byTitle : search(abnos, query).stream().findFirst().orElse(null);
    }

    static Abno byTitle(List<Abno> abnos, String title) {
        return abnos.stream().filter(abno -> abno.title().equals(title)).findFirst().orElse(null);
    }

    // 한국어 이름과 영어 문서 제목 둘 다로 찾는다
    static List<Abno> search(List<Abno> abnos, String query) {
        String needle = compact(query);
        return abnos.stream()
                .filter(abno -> compact(abno.name()).contains(needle) || compact(abno.title()).contains(needle))
                .limit(OptionData.MAX_CHOICES)
                .toList();
    }

    private static String compact(String text) {
        return text.replaceAll("\\s", "").toLowerCase(Locale.ROOT);
    }
}
