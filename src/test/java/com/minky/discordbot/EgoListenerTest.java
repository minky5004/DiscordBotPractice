package com.minky.discordbot;

import com.minky.discordbot.EgoCatalog.Ego;
import com.minky.discordbot.EgoCatalog.EgoSkill;
import com.minky.discordbot.IdentityCatalog.Passive;
import com.minky.discordbot.IdentityCatalog.SkillStats;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.section.Section;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EgoListenerTest {

    private static final Ego CAIRN = new Ego(20103, "소망석", "이상", "TETH", "https://example.com/cairn.png",
            ordered(Map.entry("나태", 4), Map.entry("우울", 1)),
            ordered(Map.entry("분노", 1.0), Map.entry("나태", 0.5), Map.entry("우울", 2.0)),
            List.of(
                    new EgoSkill(2010311, false, "소망석", "가장 뒤 대상 공격\n코인1 마비 3 부여",
                            new SkillStats("나태", "관통", 22, "+4", 1, null), 20),
                    new EgoSkill(2010321, true, "소망석", "무작위 대상 공격", null, null)),
            List.of(new Passive(2010311, "돌하르방", "매 턴 마비 면역", false, null)));

    @SafeVarargs
    private static <V> Map<String, V> ordered(Map.Entry<String, V>... entries) {
        Map<String, V> map = new LinkedHashMap<>();
        for (Map.Entry<String, V> entry : entries) {
            map.put(entry.getKey(), entry.getValue());
        }
        return map;
    }

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
    void oneScreenHoldsHeaderSkillsAndPassive() {
        assertEquals(List.of(
                        "## 소망석",
                        "-# 이상 · TETH",
                        "자원  나태 ×4 · 우울 ×1",
                        // 보통(×1.0)은 빼고 눈에 띄는 것만
                        "내성  나태 ×0.5 · 우울 ×2.0",
                        "### 각성 · 소망석\n-# 나태 · 관통 · 위력 22 · 코인 +4 × 1 · 정신력 20\n가장 뒤 대상 공격\n**코인 1** 마비 3 부여",
                        "### 침식 · 소망석\n-# 수치 없음\n무작위 대상 공격",
                        "### 패시브 · 돌하르방\n매 턴 마비 면역",
                        "-# 수치: Limbus Company Wiki (wiki.gg)"),
                texts(EgoListener.container(CAIRN)));
    }

    @Test
    void accentColourFollowsTheAwakeningSkillsSin() {
        assertEquals(IdentityListener.SIN_COLORS.get("나태"), EgoListener.container(CAIRN).getAccentColorRaw());
    }

    @Test
    void egoWithoutWikiDropsStatsLinesAndCredit() {
        Ego bare = new Ego(20103, "소망석", "이상", null, null, Map.of(), Map.of(),
                List.of(new EgoSkill(2010311, false, "소망석", "", null, null)), List.of());
        assertEquals(List.of("## 소망석", "-# 이상", "### 각성 · 소망석\n-# 수치 없음\n-# 효과 없음"),
                texts(EgoListener.container(bare)));
    }

    @Test
    void longTextStaysUnderTheMessageLimit() {
        String longText = "가".repeat(3000);
        Ego wordy = new Ego(20103, "소망석", "이상", "TETH", null, Map.of(), Map.of(),
                List.of(new EgoSkill(1, false, "각성", longText, null, null), new EgoSkill(2, true, "침식", longText, null, null)),
                List.of(new Passive(3, "패시브", longText, false, null), new Passive(4, "패시브2", longText, false, null)));
        int total = texts(EgoListener.container(wordy)).stream().mapToInt(String::length).sum();
        assertTrue(total <= 4000, "합계 " + total);
    }

    @Test
    void oneLongSkillIsNotCutWhenTheWholeScreenFits() {
        String longText = "가".repeat(1800);
        Ego ego = new Ego(20109, "긴 E.G.O", "이상", null, null, Map.of(), Map.of(),
                List.of(new EgoSkill(1, false, "각성", longText, null, null)), List.of());
        assertTrue(texts(EgoListener.container(ego)).stream().anyMatch(text -> text.endsWith(longText)));
    }

    @Test
    void searchMatchesEgoOrSinnerName() {
        List<Ego> egos = List.of(CAIRN, new Ego(20608, "오혈읍루 [汚血泣淚]", "싱클레어", null, null, Map.of(), Map.of(), List.of(), List.of()));
        assertEquals(List.of(20103), EgoListener.search(egos, "소망 석").stream().map(Ego::id).toList());
        assertEquals(List.of(20608), EgoListener.search(egos, "싱클").stream().map(Ego::id).toList());
        assertEquals(20608, EgoListener.find(egos, "20608").id());
        assertNull(EgoListener.find(egos, "없는 E.G.O"));
        assertEquals("오혈읍루 [汚血泣淚] · 싱클레어", EgoListener.label(egos.get(1)));
    }
}
