package com.minky.discordbot;

import com.minky.discordbot.GiftCatalog.Gift;
import com.minky.discordbot.GiftSearchListener.Filter;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.section.Section;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GiftListenerTest {

    private static final Gift HELLTERFLY = new Gift(9001, "지옥나비의 꿈", "분노", "II", 198, "화상", true,
            "https://example.com/gift.png", List.of("화상 3 부여", "**화상 4** 부여", "**화상 5** 부여"));

    private static final Gift DRUMSTICK = new Gift(9701, "뜨거운 육즙 다리살", "탐식", "I", 120, null, true, null, List.of("회복"));

    private static List<String> texts(Container container) {
        return container.getComponents().stream()
                .flatMap(component -> component instanceof Section section
                        ? section.getContentComponents().stream()
                        : Stream.of(component))
                .filter(TextDisplay.class::isInstance)
                .map(component -> ((TextDisplay) component).getContent())
                .toList();
    }

    @Test
    void screenShowsGradeLineAndTheChosenUpgradeText() {
        assertEquals(List.of("## 지옥나비의 꿈++", "-# 분노 · 등급 II · 화상 · 가격 198", "**화상 5** 부여",
                        "-# 수치: Limbus Company Wiki (wiki.gg)"),
                texts(GiftListener.container(HELLTERFLY, 2)));
        assertEquals(IdentityListener.SIN_COLORS.get("분노"), GiftListener.container(HELLTERFLY, 2).getAccentColorRaw());
    }

    @Test
    void upgradeButtonsDisableTheCurrentLevelAndVanishWithoutUpgrades() {
        List<Button> buttons = GiftListener.buttons(HELLTERFLY, 1);
        assertEquals(List.of("기본", "+", "++"), buttons.stream().map(Button::getLabel).toList());
        assertEquals(List.of("기프트:9001:0", "기프트:9001:1", "기프트:9001:2"), buttons.stream().map(Button::getCustomId).toList());
        assertEquals(List.of(false, true, false), buttons.stream().map(Button::isDisabled).toList());
        assertEquals(List.of(), GiftListener.buttons(DRUMSTICK, 0));
    }

    @Test
    void levelOutOfRangeFallsBackToTheBaseText() {
        assertEquals("## 뜨거운 육즙 다리살", texts(GiftListener.container(DRUMSTICK, 2)).getFirst());
    }

    @Test
    void giftWithoutWikiDropsTheGradeLineAndCredit() {
        Gift bare = new Gift(9001, "지옥나비의 꿈", null, null, null, null, false, null, List.of(""));
        assertEquals(List.of("## 지옥나비의 꿈", "-# 효과 없음"), texts(GiftListener.container(bare, 0)));
    }

    @Test
    void searchAndFindByNameOrId() {
        List<Gift> gifts = List.of(HELLTERFLY, DRUMSTICK);
        assertEquals(List.of(9701), GiftListener.search(gifts, "육즙 다리").stream().map(Gift::id).toList());
        assertEquals(9001, GiftListener.find(gifts, "9001").id());
        assertNull(GiftListener.find(gifts, "없는 기프트"));
    }

    @Test
    void filterMatchesEveryGivenConditionAndSortsHighTiersFirst() {
        Gift tierFour = new Gift(9100, "IV 화상", "분노", "IV", 300, "화상", true, null, List.of("x"));
        Gift ex = new Gift(9200, "EX 화상", "색욕", "EX", null, "화상", true, null, List.of("x"));
        List<Gift> gifts = List.of(HELLTERFLY, DRUMSTICK, tierFour, ex);

        assertEquals(List.of(9200, 9100, 9001), GiftSearchListener.search(gifts, new Filter("화상", null, null))
                .stream().map(Gift::id).toList());
        assertEquals(List.of(9100, 9001), GiftSearchListener.search(gifts, new Filter("화상", null, "분노"))
                .stream().map(Gift::id).toList());
        assertEquals(List.of(9001), GiftSearchListener.search(gifts, new Filter(null, "II", null))
                .stream().map(Gift::id).toList());
    }

    @Test
    void searchResultButtonsOpenTheGiftScreen() {
        Container container = GiftSearchListener.container(List.of(HELLTERFLY, DRUMSTICK), new Filter("화상", null, null));
        assertTrue(texts(container).contains("-# 화상"));
        assertEquals(List.of("기프트:9001:0", "기프트:9701:0"),
                GiftSearchListener.buttons(List.of(HELLTERFLY, DRUMSTICK)).stream().map(Button::getCustomId).toList());
    }

    @Test
    void longResultListIsCappedAtTwentyFive() {
        List<Gift> many = IntStream.range(0, 30)
                .mapToObj(i -> new Gift(9000 + i, "기프트" + i, "분노", "I", 1, "화상", true, null, List.of("x")))
                .toList();
        Container container = GiftSearchListener.container(many, new Filter(null, null, null));
        assertTrue(texts(container).stream().anyMatch(text -> text.contains("30건 중")));
    }
}
