package com.minky.discordbot;

import com.minky.discordbot.IdentityCatalog.Identity;
import com.minky.discordbot.IdentityCatalog.Skill;
import com.minky.discordbot.IdentityCatalog.SkillStats;
import com.minky.discordbot.IdentityCatalog.Stats;
import com.minky.discordbot.IdentitySearchListener.Filter;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IdentitySearchListenerTest {

    // 분노와 관통이 서로 다른 스킬에 나뉘어 있다
    private static final Identity SPLIT = new Identity(10101, "수감복", "이상", null,
            new Stats(3, 1, null, null, null),
            List.of(
                    new Skill(1010101, "찌르기", "", new SkillStats("오만", "관통", 4, "+3", 2, null)),
                    new Skill(1010102, "내리치기", "", new SkillStats("분노", "타격", 5, "+2", 3, null))),
            List.of());

    // 한 스킬이 분노 관통
    private static final Identity BOTH = new Identity(10201, "쥐어뜯는 손", "파우스트", null,
            new Stats(2, 3, null, null, null),
            List.of(new Skill(1020101, "할퀴기", "", new SkillStats("분노", "관통", 6, "+4", 2, null))),
            List.of());

    // 위키에서 짝을 찾지 못해 수치가 통째로 없다
    private static final Identity NO_STATS = new Identity(10901, "쇠사슬", "로쟈", null, null,
            List.of(new Skill(1090101, "휘두르기", "", new SkillStats("분노", "관통", 3, "+1", 1, null))),
            List.of());

    private static final List<Identity> IDENTITIES = List.of(SPLIT, BOTH, NO_STATS);

    private static final Filter NONE = new Filter(null, null, null, null, null);

    private static List<String> texts(Container container) {
        return container.getComponents().stream()
                .filter(TextDisplay.class::isInstance)
                .map(component -> ((TextDisplay) component).getContent())
                .toList();
    }

    private static List<Button> buttons(Container container) {
        return container.getComponents().stream()
                .filter(ActionRow.class::isInstance)
                .flatMap(component -> ((ActionRow) component).getComponents().stream())
                .filter(Button.class::isInstance)
                .map(Button.class::cast)
                .toList();
    }

    private static List<Integer> ids(List<Identity> results) {
        return results.stream().map(Identity::id).toList();
    }

    @Test
    void sinAndTypeMustMeetInOneSkill() {
        Filter filter = new Filter("분노", "관통", null, null, null);

        assertEquals(List.of(10201, 10901), ids(IdentitySearchListener.search(IDENTITIES, filter)));
    }

    @Test
    void sinAloneMatchesAnySkillOfThatSin() {
        Filter filter = new Filter("분노", null, null, null, null);

        assertEquals(List.of(10101, 10201, 10901), ids(IdentitySearchListener.search(IDENTITIES, filter)));
    }

    @Test
    void sinnerNarrowsToOneIdentity() {
        Filter filter = new Filter(null, null, "파우스트", null, null);

        assertEquals(List.of(10201), ids(IdentitySearchListener.search(IDENTITIES, filter)));
    }

    @Test
    void conditionsCombineWithAnd() {
        Filter filter = new Filter("분노", "관통", "로쟈", null, null);

        assertEquals(List.of(10901), ids(IdentitySearchListener.search(IDENTITIES, filter)));
    }

    @Test
    void identityWithoutStatsFallsOutOfRarityAndSeason() {
        assertEquals(List.of(10101), ids(IdentitySearchListener.search(IDENTITIES, new Filter(null, null, null, 3, null))));
        assertEquals(List.of(10201), ids(IdentitySearchListener.search(IDENTITIES, new Filter(null, null, null, null, 3))));
    }

    @Test
    void emptyFilterKeepsEveryIdentity() {
        assertEquals(List.of(10101, 10201, 10901), ids(IdentitySearchListener.search(IDENTITIES, NONE)));
    }

    @Test
    void listNumbersMatchButtonOrder() {
        Container container = IdentitySearchListener.container(IDENTITIES, NONE);

        assertEquals("## 인격 검색", texts(container).get(0));
        assertEquals("-# 조건 없음 · 전체", texts(container).get(1));
        assertEquals("""
                        **`1`  [수감복] 이상**
                        -# ★★★ · 시즌 1
                        **`2`  [쥐어뜯는 손] 파우스트**
                        -# ★★ · 시즌 3
                        **`3`  [쇠사슬] 로쟈**
                        -# 수치 없음""",
                texts(container).get(2));
        assertEquals(List.of("1 수감복", "2 쥐어뜯는 손", "3 쇠사슬"),
                buttons(container).stream().map(Button::getLabel).toList());
    }

    // 결과 버튼은 /인격 상세의 것과 같은 형식이라 IdentityListener 가 그대로 받는다
    @Test
    void buttonsCarryIdentityDetailIds() {
        assertEquals(List.of("인격:10101:0", "인격:10201:0", "인격:10901:0"),
                buttons(IdentitySearchListener.container(IDENTITIES, NONE)).stream()
                        .map(Button::getCustomId)
                        .toList());
    }

    // 디스코드는 빈 TextDisplay 를 거부한다
    @Test
    void noResultLeavesOneLineAndNoButton() {
        Container container = IdentitySearchListener.container(
                List.of(), new Filter("질투", null, null, null, null));

        assertEquals("-# 질투", texts(container).get(1));
        assertEquals("조건에 맞는 인격 없음", texts(container).get(2));
        assertTrue(buttons(container).isEmpty());
    }

    // 버튼 25개가 액션 행 다섯 줄을 채운다 — 넘는 만큼은 조건을 좁히라는 줄로
    @Test
    void resultsBeyondButtonLimitAreCutWithACount() {
        List<Identity> many = IntStream.range(0, 30)
                .mapToObj(i -> new Identity(20000 + i, "칭호" + i, "이상", null, null, List.of(), List.of()))
                .map(Identity.class::cast)
                .toList();

        Container container = IdentitySearchListener.container(many, NONE);

        assertEquals(25, buttons(container).size());
        assertEquals("-# 30건 중 25건 · 조건을 더 좁히세요", texts(container).get(3));
    }

    @Test
    void describeListsEveryGivenCondition() {
        assertEquals("분노 · 관통 · 이상 · ★★★ · 시즌 2",
                IdentitySearchListener.describe(new Filter("분노", "관통", "이상", 3, 2)));
    }
}
