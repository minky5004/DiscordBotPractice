package com.minky.discordbot;

import com.minky.discordbot.IdentityCatalog.Keyword;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KeywordListenerTest {

    private static final List<Keyword> KEYWORDS = List.of(
            new Keyword("Combustion", "화상", "턴 종료 시 피해", List.of("[LCB 수감자] 이상", "E.G.O 소망석 · 이상")),
            new Keyword("Sinking", "침잠", "피격 시 정신력 피해", List.of()),
            new Keyword("SinkingDeluge", "침잠 쇄도", "침잠 횟수만큼", List.of()));

    @Test
    void searchIgnoresSpacesAndMatchesAnyPart() {
        assertEquals(List.of("Sinking", "SinkingDeluge"),
                KeywordListener.search(KEYWORDS, "침 잠").stream().map(Keyword::id).toList());
        assertEquals(List.of("SinkingDeluge"), KeywordListener.search(KEYWORDS, "쇄도").stream().map(Keyword::id).toList());
    }

    @Test
    void findTakesTheAutocompleteIdFirstThenTypedText() {
        assertEquals("SinkingDeluge", KeywordListener.find(KEYWORDS, "SinkingDeluge").id());
        assertEquals("Combustion", KeywordListener.find(KEYWORDS, "화상").id());
        assertNull(KeywordListener.find(KEYWORDS, "없는 키워드"));
    }

    @Test
    void containerShowsNameDescriptionThenWhereItIsUsed() {
        Container container = KeywordListener.container(KEYWORDS.getFirst());
        List<String> texts = container.getComponents().stream()
                .filter(TextDisplay.class::isInstance)
                .map(component -> ((TextDisplay) component).getContent())
                .toList();

        assertEquals(List.of("## 화상", "턴 종료 시 피해", "-# 사용  [LCB 수감자] 이상 · E.G.O 소망석 · 이상"), texts);
    }

    @Test
    void longUserListShowsTheFirstFewAndCountsTheRest() {
        List<String> users = IntStream.rangeClosed(1, 12).mapToObj(i -> "인격" + i).toList();
        assertEquals("-# 사용  인격1 · 인격2 · 인격3 · 인격4 · 인격5 · 인격6 · 인격7 · 인격8 외 4",
                KeywordListener.users(users));
    }

    @Test
    void longDescriptionIsCutBeforeTheMessageLimit() {
        Container container = KeywordListener.container(new Keyword("Long", "긴 설명", "가".repeat(5000), List.of("나".repeat(5000))));
        assertTrue(container.getComponents().stream()
                .filter(TextDisplay.class::isInstance)
                .allMatch(component -> ((TextDisplay) component).getContent().length() <= IdentityListener.TEXT_LIMIT));
    }
}
