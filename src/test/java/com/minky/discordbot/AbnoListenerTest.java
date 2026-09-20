package com.minky.discordbot;

import com.minky.discordbot.AbnoCatalog.Abno;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.section.Section;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AbnoListenerTest {

    private static final Abno BIRD = new Abno("Punishing Bird", "징벌 새", "O-02-56", "TETH", "부리",
            List.of("부리 모양 목걸이"), "이름과 거미의 거울", "https://example.com/bird.png",
            List.of("작은 새다.", "성나면 붉어진다."));

    // 거울 던전 이벤트에서만 만나 도감 항목이 없는 환상체
    private static final Abno SOUP = new Abno("Basilisoup", "바질리스프크", null, null, null, List.of(), null, null, List.of());

    private static List<String> texts(Container container) {
        return container.getComponents().stream()
                .flatMap(component -> component instanceof Section section
                        ? section.getContentComponents().stream()
                        : Stream.of(component))
                .filter(TextDisplay.class::isInstance)
                .map(component -> ((TextDisplay) component).getContent())
                .toList();
    }

    private static List<String> labels(List<Button> buttons) {
        return buttons.stream().map(Button::getLabel).toList();
    }

    @Test
    void infoPageShowsWikiFieldsAndTheLogPageShowsThatLog() {
        assertEquals(List.of("## 징벌 새", "-# Punishing Bird · O-02-56 · TETH",
                        "**유래 E.G.O** 부리\n**E.G.O 기프트** 부리 모양 목걸이\n**등장** 이름과 거미의 거울",
                        "-# 정보: Limbus Company Wiki (wiki.gg)"),
                texts(AbnoListener.container(BIRD, 0)));
        assertEquals(List.of("## 징벌 새 · 관찰 2", "-# Punishing Bird · O-02-56 · TETH", "성나면 붉어진다.",
                        "-# 정보: Limbus Company Wiki (wiki.gg)"),
                texts(AbnoListener.container(BIRD, 2)));
    }

    @Test
    void riskGradeColorsTheContainerAndAnUnknownGradeFallsBack() {
        assertEquals(0xE0B032, AbnoListener.container(BIRD, 0).getAccentColorRaw());
        // 뒤에 붙는 내부 번호는 등급이 아니다
        assertEquals(0x8B4FD8, AbnoListener.color("WAW-05"));
        assertEquals(IdentityListener.DEFAULT_COLOR, AbnoListener.color(null));
    }

    @Test
    void logButtonsDisableTheCurrentPageAndVanishWithoutLogs() {
        assertEquals(List.of("정보", "관찰 1", "관찰 2"), labels(AbnoListener.buttons(BIRD, 1)));
        assertTrue(AbnoListener.buttons(BIRD, 1).get(1).isDisabled());
        assertEquals("환상체:Punishing Bird:2", AbnoListener.buttons(BIRD, 1).get(2).getCustomId());
        assertEquals(List.of(), AbnoListener.buttons(SOUP, 0));
    }

    @Test
    void pageOutOfRangeFallsBackToTheInfoPage() {
        assertEquals(texts(AbnoListener.container(BIRD, 0)), texts(AbnoListener.container(BIRD, 3)));
    }

    @Test
    void abnormalityWithoutWikiFieldsStillFillsTheScreen() {
        assertEquals(List.of("## 바질리스프크", "-# Basilisoup", "-# 위키에 적힌 정보 없음",
                        "-# 정보: Limbus Company Wiki (wiki.gg)"),
                texts(AbnoListener.container(SOUP, 0)));
    }

    @Test
    void searchMatchesKoreanNameAndEnglishTitle() {
        List<Abno> abnos = List.of(BIRD, SOUP);

        assertEquals(List.of(BIRD), AbnoListener.search(abnos, "징벌"));
        assertEquals(List.of(BIRD), AbnoListener.search(abnos, "punishing"));
        // 자동완성이 넘기는 값은 위키 문서 제목
        assertEquals(SOUP, AbnoListener.find(abnos, "Basilisoup"));
    }
}
