package com.minky.discordbot;

import com.minky.discordbot.IdentityCatalog.Identity;
import com.minky.discordbot.IdentityCatalog.Keyword;
import com.minky.discordbot.IdentityCatalog.Passive;
import com.minky.discordbot.IdentityCatalog.Skill;
import com.minky.discordbot.IdentityCatalog.SkillStats;
import com.minky.discordbot.IdentityCatalog.Stats;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.section.Section;
import net.dv8tion.jda.api.components.selections.SelectOption;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.components.thumbnail.Thumbnail;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IdentityListenerTest {

    private static final Identity SOLEMN_LAMENT = new Identity(10110, "로보토미 E.G.O::엄숙한 애도", "이상", null,
            new Stats(3, null, 1.0, 0.5, null),
            List.of(
                    new Skill(1011001, "떠난이에게 축하를", "[합 승리시] 침잠 횟수 2 증가\n코인1 나비 부여",
                            new SkillStats("오만", "관통", 4, "+4", 2, null)),
                    new Skill(1011004, "관에서나비가날아오리라", "", new SkillStats(null, "방어", 10, "+4", null, null))),
            List.of(
                    new Passive(1011011, "쏘아라.쏘으리로다.", "탄환을 얻으면", false, "우울 3 공명"),
                    new Passive(1011021, "구원의 손", "서포트 문구", true, null)));

    private static String thumbnail(Container container) {
        return container.getComponents().stream()
                .filter(Section.class::isInstance)
                .map(component -> ((Section) component).getAccessory())
                .filter(Thumbnail.class::isInstance)
                .map(accessory -> ((Thumbnail) accessory).getUrl())
                .findFirst()
                .orElse(null);
    }

    // 썸네일이 붙으면 머리가 Section 안으로 들어가므로 한 겹 더 편다
    private static List<String> texts(Container container) {
        return container.getComponents().stream()
                .flatMap(component -> component instanceof Section section
                        ? section.getContentComponents().stream()
                        : Stream.of(component))
                .filter(TextDisplay.class::isInstance)
                .map(component -> ((TextDisplay) component).getContent())
                .toList();
    }

    private static final List<Keyword> KEYWORDS = List.of(
            new Keyword("Combustion", "화상", "턴 종료 시 피해", List.of("[LCB 수감자] 이상")),
            new Keyword("Sinking", "침잠", "피격 시 정신력 피해", List.of("[LCB 수감자] 이상", "[로보토미 E.G.O::엄숙한 애도] 이상")),
            new Keyword("SinkingWhite", "나비", "특수 침잠", List.of("[로보토미 E.G.O::엄숙한 애도] 이상")));

    private static StringSelectMenu menu(Container container) {
        return container.getComponents().stream()
                .filter(ActionRow.class::isInstance)
                .flatMap(row -> ((ActionRow) row).getComponents().stream())
                .filter(StringSelectMenu.class::isInstance)
                .map(StringSelectMenu.class::cast)
                .findFirst()
                .orElse(null);
    }

    @Test
    void menuOffersOnlyKeywordsThisIdentityUses() {
        StringSelectMenu menu = menu(IdentityListener.container(SOLEMN_LAMENT, 1, KEYWORDS));

        assertEquals("키워드", menu.getCustomId());
        assertEquals(List.of("침잠", "나비"), menu.getOptions().stream().map(SelectOption::getLabel).toList());
        assertEquals(List.of("Sinking", "SinkingWhite"), menu.getOptions().stream().map(SelectOption::getValue).toList());
    }

    @Test
    void identityWithoutKeywordsGetsNoMenu() {
        Identity lcb = new Identity(10201, "LCB 수감자", "파우스트", null, null, List.of(), List.of());

        assertNull(menu(IdentityListener.container(lcb, 0, KEYWORDS)));
    }

    @Test
    void menuIsDroppedWhenButtonsFillTheMessage() {
        // 버튼 25개면 컴포넌트가 이미 40개 한도에 닿는다 · 메뉴 두 개(행 · 메뉴)를 더하면 응답 전체가 거부된다
        List<Skill> skills = IntStream.range(0, 40)
                .mapToObj(i -> new Skill(1011000 + i, "스킬" + i, "원문", null))
                .toList();
        Identity many = new Identity(10110, "로보토미 E.G.O::엄숙한 애도", "이상", null, null, skills, List.of());

        assertNull(menu(IdentityListener.container(many, 0, KEYWORDS)));
    }

    @Test
    void menuKeepsWithinDiscordOptionLimit() {
        List<Keyword> many = IntStream.range(0, 30)
                .mapToObj(i -> new Keyword("K" + i, "키워드" + i, "설명", List.of("[로보토미 E.G.O::엄숙한 애도] 이상")))
                .toList();

        assertEquals(StringSelectMenu.OPTIONS_MAX_AMOUNT, menu(IdentityListener.container(SOLEMN_LAMENT, 0, many)).getOptions().size());
    }

    @Test
    void firstPageListsEveryEntryWithItsNumbers() {
        List<String> texts = texts(IdentityListener.container(SOLEMN_LAMENT, 0, List.of()));

        assertEquals("## [로보토미 E.G.O::엄숙한 애도] 이상 · ★★★", texts.get(0));
        assertEquals("내성  참격 ×1.0 · 관통 ×0.5", texts.get(1));
        assertEquals("""
                        **`1`  떠난이에게 축하를**
                        -# 오만 · 관통 · 위력 4 · 코인 +4 × 2
                        **`2`  관에서나비가날아오리라**
                        -# 방어 · 위력 10 · 코인 +4
                        **`3`  패시브 · 쏘아라.쏘으리로다.**
                        -# 우울 3 공명
                        **`4`  서포트 패시브 · 구원의 손**
                        -# 조건 없음""",
                texts.get(2));
        assertEquals("-# 수치: Limbus Company Wiki (wiki.gg)", texts.get(3));
    }

    @Test
    void detailPagePutsTheSkillFirstAndTheIdentityInSmallText() {
        List<String> texts = texts(IdentityListener.container(SOLEMN_LAMENT, 1, List.of()));

        // 상세에서는 스킬이 제목 · 인격은 작은 글씨로 물러나고 수치는 본문 크기로 올라온다
        assertEquals("### 떠난이에게 축하를", texts.get(0));
        assertEquals("-# [로보토미 E.G.O::엄숙한 애도] 이상 · ★★★", texts.get(1));
        assertEquals("오만 · 관통 · 위력 4 · 코인 +4 × 2", texts.get(2));
        assertEquals("[합 승리시] 침잠 횟수 2 증가\n**코인 1** 나비 부여", texts.get(3));
    }

    @Test
    void coinLinesGetTheirOwnBoldLabel() {
        Identity identity = new Identity(10101, "LCB 수감자", "이상", null, null,
                List.of(new Skill(1010101, "지우기", "코인1 첫 효과\n코인2 둘째 효과\n코인이 아닌 줄", null)), List.of());

        assertEquals("**코인 1** 첫 효과\n**코인 2** 둘째 효과\n코인이 아닌 줄",
                texts(IdentityListener.container(identity, 1, List.of())).get(3));
    }

    @Test
    void emptySkillTextStillRendersSomething() {
        // 디스코드는 빈 TextDisplay 를 거부한다
        assertEquals("-# 효과 없음", texts(IdentityListener.container(SOLEMN_LAMENT, 2, List.of())).get(3));
    }

    @Test
    void skillPageUsesItsOwnIconAndFallsBackToTheIdentityArt() {
        Identity identity = new Identity(10110, "로보토미 E.G.O::엄숙한 애도", "이상", "https://wiki/Full.png", null,
                List.of(new Skill(1011001, "아이콘 있음", "원문", new SkillStats(null, null, null, null, null, "https://wiki/Icon.png")),
                        new Skill(1011002, "아이콘 없음", "원문", null)),
                List.of(new Passive(1011011, "패시브", "원문", false, null)));

        assertEquals("https://wiki/Icon.png", thumbnail(IdentityListener.container(identity, 1, List.of())));
        // 위키에서 짝을 못 찾은 스킬과 패시브 · 목록은 인격 일러스트로
        assertEquals("https://wiki/Full.png", thumbnail(IdentityListener.container(identity, 2, List.of())));
        assertEquals("https://wiki/Full.png", thumbnail(IdentityListener.container(identity, 3, List.of())));
        assertEquals("https://wiki/Full.png", thumbnail(IdentityListener.container(identity, 0, List.of())));
    }

    @Test
    void identityWithoutArtGetsNoSection() {
        // 썸네일이 없으면 섹션도 없이 머리를 그대로 쌓는다
        assertNull(thumbnail(IdentityListener.container(SOLEMN_LAMENT, 0, List.of())));
        assertEquals("## [로보토미 E.G.O::엄숙한 애도] 이상 · ★★★",
                texts(IdentityListener.container(SOLEMN_LAMENT, 0, List.of())).get(0));
    }

    @Test
    void accentColourFollowsTheSkillsSin() {
        // 상세는 그 스킬의 죄악 · 목록은 가장 많이 쓰인 죄악
        assertEquals(0x3A6FD8, IdentityListener.container(SOLEMN_LAMENT, 1, List.of()).getAccentColorRaw());
        assertEquals(0x3A6FD8, IdentityListener.container(SOLEMN_LAMENT, 0, List.of()).getAccentColorRaw());
    }

    @Test
    void defenceSkillsDoNotVoteForTheListColour() {
        // 수비 스킬은 죄악이 없다 — 표에 끼워 두면 공격 스킬과 동수가 되어 회색이 이길 수 있다
        Identity identity = new Identity(10101, "LCB 수감자", "이상", null, null,
                List.of(new Skill(1010101, "분노 스킬", "원문", new SkillStats("분노", "타격", 2, "+3", 3, null)),
                        new Skill(1010102, "수비", "원문", new SkillStats(null, "방어", 10, "+4", 1, null)),
                        new Skill(1010103, "수비2", "원문", new SkillStats(null, "방어", 10, "+4", 1, null))),
                List.of());

        assertEquals(0xDC3545, IdentityListener.container(identity, 0, List.of()).getAccentColorRaw());
    }

    @Test
    void identityWithNoEntriesStillRenders() {
        // 게임 파일에 스킬 행이 없는 인격 — 빈 TextDisplay 는 디스코드가 거부한다
        Identity empty = new Identity(10101, "LCB 수감자", "이상", null, null, List.of(), List.of());

        assertEquals(List.of("## [LCB 수감자] 이상", "-# 스킬 · 패시브를 읽지 못함"),
                texts(IdentityListener.container(empty, 0, List.of())));
        assertEquals(List.of(), IdentityListener.buttons(empty, 0));
    }

    @Test
    void pageBeyondTheListFallsBackToIt() {
        // 24시간 갱신이 항목을 줄이면 옛 메시지의 버튼이 범위 밖 번호를 들고 온다
        assertEquals(texts(IdentityListener.container(SOLEMN_LAMENT, 0, List.of())),
                texts(IdentityListener.container(SOLEMN_LAMENT, 9, List.of())));
    }

    @Test
    void identityWithoutWikiStatsDropsResistsAndCredit() {
        Identity identity = new Identity(10101, "LCB 수감자", "이상", null, null,
                List.of(new Skill(1010101, "지우기", "원문", null)),
                List.of(new Passive(1010101, "정보전달", "패시브 원문", false, null)));

        List<String> texts = texts(IdentityListener.container(identity, 0, List.of()));

        assertEquals("## [LCB 수감자] 이상", texts.get(0));
        assertEquals(List.of("**`1`  지우기**", "-# 수치 없음", "**`2`  패시브 · 정보전달**", "-# 조건 없음"),
                List.of(texts.get(1).split("\n")));
        assertEquals(2, texts.size());
    }

    @Test
    void longTextIsCutBeforeTheMessageLimit() {
        List<Skill> skills = IntStream.range(0, 9)
                .mapToObj(i -> new Skill(1111500 + i, "스킬" + i, "가".repeat(3000), null))
                .toList();
        Identity identity = new Identity(11115, "거미집 중지 아비", "그레고르", null, null, skills, List.of());

        for (String text : texts(IdentityListener.container(identity, 1, List.of()))) {
            assertTrue(text.length() <= 2000, "덩이 하나가 2000자를 넘는다: " + text.length());
        }
    }

    @Test
    void buttonsCarryNamesSoTheListIsNotTheOnlyWayToKnow() {
        List<Button> onList = IdentityListener.buttons(SOLEMN_LAMENT, 0);

        // 패시브는 종류를 떼고 이름만 남긴다
        assertEquals(List.of("목록", "1 떠난이에게 축하를", "2 관에서나비가날아오리라", "3 쏘아라.쏘으리로다.", "4 구원의 손"),
                onList.stream().map(Button::getLabel).toList());
        assertEquals(List.of("인격:10110:0", "인격:10110:1", "인격:10110:2", "인격:10110:3", "인격:10110:4"),
                onList.stream().map(Button::getCustomId).toList());
        assertEquals(List.of(true, false, false, false, false), onList.stream().map(Button::isDisabled).toList());
        assertEquals(List.of(false, false, true, false, false),
                IdentityListener.buttons(SOLEMN_LAMENT, 2).stream().map(Button::isDisabled).toList());
    }

    @Test
    void longNameIsCutSoFiveButtonsStillFitARow() {
        Identity identity = new Identity(11115, "거미집 중지 아비", "그레고르", null, null,
                List.of(new Skill(1111501, "내 헤어쿠포오오오온!!!!", "원문", null),
                        new Skill(1111502, "짧은 이름", "원문", null)), List.of());

        assertEquals(List.of("목록", "1 내 헤어쿠포오오오온!…", "2 짧은 이름"),
                IdentityListener.buttons(identity, 0).stream().map(Button::getLabel).toList());
    }

    @Test
    void everyPageKeepsButtonCustomIdsDistinct() {
        // 한 메시지 안에 같은 customId 가 둘이면 디스코드가 50035 로 응답 전체를 거부한다
        for (int page = 0; page <= 4; page++) {
            List<String> ids = IdentityListener.buttons(SOLEMN_LAMENT, page).stream().map(Button::getCustomId).toList();
            assertEquals(ids.size(), Set.copyOf(ids).size(), "페이지 " + page + " 의 customId 가 겹친다");
        }
    }

    @Test
    void singleEntryIdentityGetsNoButtons() {
        Identity oneSkill = new Identity(10101, "LCB 수감자", "이상", null, null,
                List.of(new Skill(1010101, "지우기", "원문", null)), List.of());

        assertEquals(List.of(), IdentityListener.buttons(oneSkill, 0));
    }

    @Test
    void buttonsStayWithinOneMessageWorthOfRows() {
        List<Skill> skills = IntStream.range(0, 40)
                .mapToObj(i -> new Skill(1111500 + i, "스킬" + i, "원문", null))
                .toList();
        Identity many = new Identity(11115, "거미집 중지 아비", "그레고르", null, null, skills, List.of());

        // 행 5개 × 버튼 5개가 한 메시지 한도 — 넘기면 디스코드가 거부한다
        assertEquals(25, IdentityListener.buttons(many, 0).size());
    }

    @Test
    void searchMatchesTitleOrSinnerIgnoringSpaces() {
        Identity lcb = new Identity(10101, "LCB 수감자", "이상", null, null, List.of(), List.of());
        Identity faust = new Identity(10201, "LCB 수감자", "파우스트", null, null, List.of(), List.of());
        List<Identity> identities = List.of(lcb, faust, SOLEMN_LAMENT);

        assertEquals(List.of(SOLEMN_LAMENT), IdentityListener.search(identities, "엄숙한애도"));
        assertEquals(List.of(lcb, SOLEMN_LAMENT), IdentityListener.search(identities, " 이상"));
        assertEquals(identities, IdentityListener.search(identities, ""));
    }

    @Test
    void findPrefersChoiceIdOverTypedText() {
        Identity lcb = new Identity(10101, "LCB 수감자", "이상", null, null, List.of(), List.of());
        List<Identity> identities = List.of(lcb, SOLEMN_LAMENT);

        assertEquals(SOLEMN_LAMENT, IdentityListener.find(identities, "10110"));
        assertEquals(SOLEMN_LAMENT, IdentityListener.find(identities, "엄숙"));
        assertNull(IdentityListener.find(identities, "없는 인격"));
    }

    @Test
    void buttonLookupNeverFallsBackToTextSearch() {
        // 버튼 ID 의 인격이 목록에서 빠졌을 때 · 글자 검색까지 타면 숫자를 품은 다른 인격이 걸린다
        Identity decoy = new Identity(10101, "10110 번 수감자", "이상", null, null, List.of(), List.of());
        List<Identity> identities = List.of(decoy);

        assertEquals(decoy, IdentityListener.find(identities, "10110"));
        assertNull(IdentityListener.byId(identities, "10110"));
    }

    @Test
    void choiceNameFitsDiscordLimit() {
        Identity identity = new Identity(1, "가".repeat(120), "이상", null, null, List.of(), List.of());

        assertEquals("[로보토미 E.G.O::엄숙한 애도] 이상", IdentityListener.label(SOLEMN_LAMENT));
        assertEquals(100, IdentityListener.label(identity).length());
    }
}
