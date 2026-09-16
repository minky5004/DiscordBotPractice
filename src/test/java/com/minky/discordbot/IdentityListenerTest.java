package com.minky.discordbot;

import com.minky.discordbot.IdentityCatalog.Identity;
import com.minky.discordbot.IdentityCatalog.Passive;
import com.minky.discordbot.IdentityCatalog.Skill;
import com.minky.discordbot.IdentityCatalog.SkillStats;
import com.minky.discordbot.IdentityCatalog.Stats;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.MessageEmbed.Field;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IdentityListenerTest {

    private static final Identity SOLEMN_LAMENT = new Identity(10110, "로보토미 E.G.O::엄숙한 애도", "이상",
            new Stats(3, null, 1.0, 0.5, null),
            List.of(
                    new Skill(1011001, "떠난이에게 축하를", "[합 승리시] 침잠 횟수 2 증가\n코인1 나비 부여",
                            new SkillStats("오만", "관통", 4, "+4", 2)),
                    new Skill(1011004, "관에서나비가날아오리라", "", new SkillStats(null, "방어", 10, "+4", null))),
            List.of(
                    new Passive(1011011, "쏘아라.쏘으리로다.", "탄환을 얻으면", false, "우울 3 공명"),
                    new Passive(1011021, "구원의 손", "서포트 문구", true, null)));

    @Test
    void embedShowsStatsNextToGameText() {
        MessageEmbed embed = IdentityListener.embed(SOLEMN_LAMENT);

        assertEquals("[로보토미 E.G.O::엄숙한 애도] 이상 · ★★★", embed.getTitle());
        assertEquals("내성  참격 ×1.0 · 관통 ×0.5", embed.getDescription());
        assertEquals(List.of(
                        "떠난이에게 축하를 · 오만 · 관통 · 위력 4 · 코인 +4 × 2",
                        "관에서나비가날아오리라 · 방어 · 위력 10 · 코인 +4",
                        "패시브 · 쏘아라.쏘으리로다. · 우울 3 공명",
                        "서포트 패시브 · 구원의 손"),
                embed.getFields().stream().map(Field::getName).toList());
        assertEquals(List.of("[합 승리시] 침잠 횟수 2 증가\n코인1 나비 부여", "-", "탄환을 얻으면", "서포트 문구"),
                embed.getFields().stream().map(Field::getValue).toList());
        assertEquals("수치: Limbus Company Wiki (wiki.gg)", embed.getFooter().getText());
    }

    @Test
    void embedWithoutWikiStatsShowsGameTextOnly() {
        Identity identity = new Identity(10101, "LCB 수감자", "이상", null,
                List.of(new Skill(1010101, "지우기", "원문", null)),
                List.of(new Passive(1010101, "정보전달", "패시브 원문", false, null)));

        MessageEmbed embed = IdentityListener.embed(identity);

        assertEquals("[LCB 수감자] 이상", embed.getTitle());
        assertNull(embed.getDescription());
        assertEquals(List.of("지우기", "패시브 · 정보전달"), embed.getFields().stream().map(Field::getName).toList());
        assertNull(embed.getFooter());
    }

    @Test
    void embedKeepsSkillsWithSameHeading() {
        // 실데이터 5개 인격에 제목이 같은 스킬 · 패시브가 있다
        Identity identity = new Identity(10101, "LCB 수감자", "이상", null,
                List.of(new Skill(1010104, "수비", "첫째", null), new Skill(1010105, "수비", "둘째", null)), List.of());

        assertEquals(List.of("첫째", "둘째"), IdentityListener.embed(identity).getFields().stream().map(Field::getValue).toList());
    }

    @Test
    void embedCutsLongTextToDiscordLimits() {
        List<Skill> skills = IntStream.range(0, 9)
                .mapToObj(i -> new Skill(1111500 + i, "스킬" + i, "가".repeat(3000), null))
                .toList();
        Identity identity = new Identity(11115, "거미집 중지 아비", "그레고르", null, skills, List.of());

        // 6000자를 넘기면 build() 가 예외를 던진다
        MessageEmbed embed = IdentityListener.embed(identity);

        assertTrue(embed.getLength() <= MessageEmbed.EMBED_MAX_LENGTH_BOT);
        for (Field field : embed.getFields()) {
            assertTrue(field.getValue().length() <= MessageEmbed.VALUE_MAX_LENGTH);
            assertTrue(field.getValue().endsWith("…"));
        }
    }

    @Test
    void searchMatchesTitleOrSinnerIgnoringSpaces() {
        Identity lcb = new Identity(10101, "LCB 수감자", "이상", null, List.of(), List.of());
        Identity faust = new Identity(10201, "LCB 수감자", "파우스트", null, List.of(), List.of());
        List<Identity> identities = List.of(lcb, faust, SOLEMN_LAMENT);

        assertEquals(List.of(SOLEMN_LAMENT), IdentityListener.search(identities, "엄숙한애도"));
        assertEquals(List.of(lcb, SOLEMN_LAMENT), IdentityListener.search(identities, " 이상"));
        assertEquals(identities, IdentityListener.search(identities, ""));
    }

    @Test
    void findPrefersChoiceIdOverTypedText() {
        Identity lcb = new Identity(10101, "LCB 수감자", "이상", null, List.of(), List.of());
        List<Identity> identities = List.of(lcb, SOLEMN_LAMENT);

        assertEquals(SOLEMN_LAMENT, IdentityListener.find(identities, "10110"));
        assertEquals(SOLEMN_LAMENT, IdentityListener.find(identities, "엄숙"));
        assertNull(IdentityListener.find(identities, "없는 인격"));
    }

    @Test
    void choiceNameFitsDiscordLimit() {
        Identity identity = new Identity(1, "가".repeat(120), "이상", null, List.of(), List.of());

        assertEquals("[로보토미 E.G.O::엄숙한 애도] 이상", IdentityListener.label(SOLEMN_LAMENT));
        assertEquals(100, IdentityListener.label(identity).length());
    }
}
