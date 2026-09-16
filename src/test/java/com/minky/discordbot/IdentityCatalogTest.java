package com.minky.discordbot;

import com.minky.discordbot.IdentityCatalog.Identity;
import com.minky.discordbot.IdentityCatalog.Passive;
import com.minky.discordbot.IdentityCatalog.Skill;
import com.minky.discordbot.IdentityCatalog.SkillStats;
import com.minky.discordbot.IdentityCatalog.Stats;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class IdentityCatalogTest {

    @Test
    void wikiTitleJoinsTitleLinesAndName() {
        assertEquals("LCB Sinner Yi Sang", IdentityCatalog.wikiTitle("LCB\nSinner", "Yi Sang"));
        assertEquals("Lobotomy E.G.O::Solemn Lament Yi Sang", IdentityCatalog.wikiTitle("Lobotomy E.G.O::\nSolemn Lament", "Yi Sang"));
        assertEquals("Lobotomy E.G.O::Hornet【Alteration】Meursault",
                IdentityCatalog.wikiTitle("Lobotomy E.G.O::\nHornet 【Alteration】", "Meursault"));
        assertEquals("The Index Proselyte:【Paper Slip】Faust", IdentityCatalog.wikiTitle("The Index Proselyte:\n【Paper Slip】", "Faust"));
        assertEquals("The Index Proxy - Effloresced E.G.O::Procuration Don Quixote",
                IdentityCatalog.wikiTitle("The Index Proxy - \nEffloresced E.G.O::\nProcuration", "Don Quixote"));
    }

    @Test
    void templatesSplitTopLevelParamsOnly() {
        String wikitext = """
                {{IDPage
                <!--General Info-->
                |rarity=3
                |skill1={{UptieSkills
                |name=Deflect
                |ce1={{SkillCon|On Hit}} Inflict 2 {{StatusEffect|Sinking|d}}
                }}
                }}
                {{Passive|sin=Gloom|req=2|Jolly Antlers|Gain 1 {{StatusEffect|Charge|b}}}}""";

        Map<String, String> page = IdentityCatalog.templates(wikitext, "IDPage").getFirst();
        assertEquals("3", page.get("rarity"));
        assertEquals(List.of("Deflect"), IdentityCatalog.templates(wikitext, "UptieSkills").stream().map(t -> t.get("name")).toList());
        assertEquals("{{SkillCon|On Hit}} Inflict 2 {{StatusEffect|Sinking|d}}",
                IdentityCatalog.templates(wikitext, "UptieSkills").getFirst().get("ce1"));

        Map<String, String> passive = IdentityCatalog.templates(wikitext, "Passive").getFirst();
        assertEquals("Gloom", passive.get("sin"));
        assertEquals("Jolly Antlers", passive.get("1"));
        assertEquals("Gain 1 {{StatusEffect|Charge|b}}", passive.get("2"));
    }

    @Test
    void richTextReplacesKnownTagsAndStripsUnityMarkupOnly() {
        Map<String, String> tags = Map.of("OnSucceedAttack", "[적중시]", "BulletLament", "<s>탄환</s>");

        assertEquals("보유한 탄환가 10 이상이면 <라만차랜드> 합 위력 +1\n[적중시] [Unknown] 1 부여",
                IdentityCatalog.richText(
                        "<style=\"highlight\">보유한 [BulletLament]가 10 이상이면</style> <라만차랜드> <color=#e30000>합 위력 +1</color>\n"
                                + "[OnSucceedAttack] <link=\"Breath\"><sprite name=\"Breath\"><u><s>[Unknown]</s></u></link> 1 부여",
                        tags));
    }

    @Test
    void buildMatchesSkillsAndPassivesByEnglishName() {
        Map<String, byte[]> files = files(
                "KR_Personalities.json", """
                        {"dataList":[
                          {"id":10110,"title":"로보토미 E.G.O::\\n엄숙한 애도","name":"이상"},
                          {"id":9999,"title":"붉은시선","name":"베르길리우스"},
                          {"id":40501,"title":"고향을 떠나야 했던 어느 밤","name":"뫼르소"}]}""",
                "EN_Personalities.json", """
                        {"dataList":[
                          {"id":10110,"title":"Lobotomy E.G.O::\\nSolemn Lament","name":"Yi Sang"},
                          {"id":9999,"title":"The Red Gaze","name":"Vergilius"},
                          {"id":40501,"title":"A Moonlit Farewell to Home","name":"Meursault"}]}""",
                // 게임 ID 순서와 위키 칸 순서가 달라도 이름으로 짝이 맞아야 한다
                "KR_Skills.json", """
                        {"dataList":[
                          {"id":1011001,"levelList":[
                            {"level":1,"name":"낮은 단계","desc":"버려지는 문구"},
                            {"level":4,"name":"떠난이에게 축하를","desc":"[WinDuel] [Sinking] 횟수 2 증가",
                             "coinlist":[{"coindescs":[{"desc":"[BulletLament] 1 소모"},{"desc":"[OnSucceedAttack] 부여"}]},{}]}]},
                          {"id":1011002,"levelList":[{"level":4,"name":"수비","desc":""}]},
                          {"id":1011003,"levelList":[{"level":4,"name":"위키에 없는 스킬","desc":"원문"}]},
                          {"id":999901,"levelList":[{"level":1,"name":"적 스킬","desc":""}]}]}""",
                "EN_Skills_personality-01.json", """
                        {"dataList":[
                          {"id":1011001,"levelList":[{"level":1,"name":"Old Name"},{"level":4,"name":"Chainstrike"}]},
                          {"id":1011002,"levelList":[{"level":4,"name":"Guard"}]},
                          {"id":1011003,"levelList":[{"level":4,"name":"Missing"}]}]}""",
                "KR_Passives.json", """
                        {"dataList":[
                          {"id":1011001,"name":"쏘아라","desc":"동기화 3 이하 문구"},
                          {"id":1011011,"name":"쏘아라","desc":"[BulletLament]를 얻으면"},
                          {"id":1011021,"name":"구원의 손","desc":"서포트 문구"}]}""",
                "EN_Passives.json", """
                        {"dataList":[
                          {"id":1011001,"name":"Shoot"},
                          {"id":1011011,"name":"Shoot"},
                          {"id":1011021,"name":"Hand of Salvation"}]}""",
                "KR_Bufs-walpu4.json", """
                        {"dataList":[{"id":"BulletLament","name":"탄환"},{"id":"Sinking","name":"침잠"}]}""",
                "KR_SkillTag.json", """
                        {"dataList":[{"id":"OnSucceedAttack","name":"[적중시]"},{"id":"WinDuel","name":"[합 승리시]"}]}""",
                // dataList 가 없거나 id 가 문자열인 파일은 건너뛴다
                "KR_Personality_Get_Condition.json", """
                        {"dataList":[{"id":"10102_getCondition_normal","content":"획득"}]}""");
        String wikitext = """
                {{IDPage
                |rarity=3
                |season=0
                |slash=Fatal
                |pierce=Ineff.
                |blunt=2
                |skill1={{UptieSkills
                |sin=Def
                |name=Guard
                |type=Guard
                |icon=Guard Yi Sang Icon
                |spower=10
                |cpower=+ 4
                |coin=1
                }}
                |skill2={{UptieSkills
                |sin=gloom
                |name=Chainstrike [<b>連擊</b>]
                |type=Pierce
                |spower=3
                |3spower=2
                |cpower=+ 2
                |coin=2
                }}
                <!--Passive-->
                |3passive1={{Passive|Shoot|sin=Lust|req=4 Owned|동기화 3 이하는 숫자 접두 칸}}
                |passive1={{Passive|Shoot|sin=Lust|req=5 Owned|영어 설명}}
                |passive2={{Passive|sin=Gloom|req=2|sin2=Envy|req2=3 Res.|Hand of Salvation|영어 설명}}
                }}""";

        List<String> requested = new ArrayList<>();
        List<Identity> identities = IdentityCatalog.build(files, titles -> {
            requested.addAll(titles);
            return Map.of("Lobotomy E.G.O::Solemn Lament Yi Sang", wikitext);
        });

        assertEquals(List.of("Lobotomy E.G.O::Solemn Lament Yi Sang"), requested);
        assertEquals(List.of(new Identity(10110, "로보토미 E.G.O::엄숙한 애도", "이상", null,
                        new Stats(3, 0, 2.0, 0.5, 2.0),
                        List.of(
                                new Skill(1011001, "떠난이에게 축하를",
                                        "[합 승리시] 침잠 횟수 2 증가\n코인1 탄환 1 소모 · [적중시] 부여",
                                        new SkillStats("우울", "관통", 3, "+2", 2, null)),
                                new Skill(1011002, "수비", "", new SkillStats(null, "방어", 10, "+4", 1,
                                        "https://limbuscompany.wiki.gg/wiki/Special:FilePath/Guard%20Yi%20Sang%20Icon.png")),
                                new Skill(1011003, "위키에 없는 스킬", "원문", null)),
                        List.of(
                                new Passive(1011011, "쏘아라", "탄환를 얻으면", false, "색욕 5 보유"),
                                new Passive(1011021, "구원의 손", "서포트 문구", true, "우울 2 · 질투 3 공명")))),
                identities);
    }

    @Test
    void buildWithoutIdentityPageKeepsTextWithoutStats() {
        Map<String, byte[]> files = files(
                "KR_Personalities.json", """
                        {"dataList":[{"id":10101,"title":"LCB\\n수감자","name":"이상"}]}""",
                "EN_Personalities.json", """
                        {"dataList":[{"id":10101,"title":"LCB\\nSinner","name":"Yi Sang"}]}""",
                "KR_Skills.json", """
                        {"dataList":[{"id":1010101,"levelList":[{"level":3,"name":"지우기","desc":"원문"}]}]}""",
                "EN_Skills.json", """
                        {"dataList":[{"id":1010101,"levelList":[{"level":3,"name":"Deflect"}]}]}""",
                "KR_Passives.json", """
                        {"dataList":[{"id":1010101,"name":"정보전달","desc":"패시브 원문"}]}""",
                "EN_Passives.json", """
                        {"dataList":[{"id":1010101,"name":"Information Transfer"}]}""");

        // 리다이렉트로 목록 문서가 온 경우 · 문서가 아예 없는 경우 모두 수치 없음
        for (Map<String, String> wiki : List.of(Map.<String, String>of(), Map.of("LCB Sinner Yi Sang", "{{List of Identities}}"))) {
            Identity identity = IdentityCatalog.build(files, titles -> wiki).getFirst();

            assertNull(identity.stats());
            assertEquals(List.of(new Skill(1010101, "지우기", "원문", null)), identity.skills());
            assertEquals(List.of(new Passive(1010101, "정보전달", "패시브 원문", false, null)), identity.passives());
        }
    }

    @Test
    void buildSurvivesRowsTheGameFilesDoNotLineUp() {
        Map<String, byte[]> files = files(
                // KR 텍스트가 EN 보다 먼저 들어온 인격 — 위키 제목을 만들 수 없다
                "KR_Personalities.json", """
                        {"dataList":[{"id":10101,"title":"LCB\\n수감자","name":"이상"},{"id":10999,"title":"새 인격","name":"이상"}]}""",
                "EN_Personalities.json", """
                        {"dataList":[{"id":10101,"title":"LCB\\nSinner","name":"Yi Sang"}]}""",
                // levelList 가 없거나 빈 스킬 행
                "KR_Skills.json", """
                        {"dataList":[
                          {"id":1010101,"levelList":[]},
                          {"id":1010102},
                          {"id":1010103,"levelList":[{"level":4,"name":"지우기","desc":"원문"}]}]}""",
                "EN_Skills.json", """
                        {"dataList":[{"id":1010103,"levelList":[{"level":4,"name":"Deflect"}]}]}""");

        // 위키를 한 번도 받지 못한 상태의 불변 빈 맵
        List<Identity> identities = IdentityCatalog.build(files, titles -> Map.of());

        assertEquals(List.of(10101, 10999), identities.stream().map(Identity::id).toList());
        assertEquals(List.of(new Skill(1010103, "지우기", "원문", null)), identities.getFirst().skills());
    }

    @Test
    void wikiPagesAreKeyedByRequestedTitle() {
        String json = """
                {"batchcomplete":true,"query":{
                  "normalized":[{"fromencoded":false,"from":"lCB_Sinner_Faust","to":"LCB Sinner Faust"}],
                  "redirects":[{"from":"The Red Gaze Vergilius","to":"Vergilius/Assist Unit"}],
                  "pages":[
                    {"ns":0,"title":"No Such Identity","missing":true},
                    {"pageid":2661,"ns":0,"title":"Vergilius/Assist Unit","revisions":[{"slots":{"main":{"content":"{{TabbedHeader}}"}}}]},
                    {"pageid":3668,"ns":0,"title":"LCB Sinner Faust","revisions":[{"slots":{"main":{"content":"{{IDPage|rarity=1}}"}}}]}]}}""";

        assertEquals(Map.of(
                        "lCB_Sinner_Faust", "{{IDPage|rarity=1}}",
                        "The Red Gaze Vergilius", "{{TabbedHeader}}"),
                IdentityCatalog.wikiPages(json, List.of("lCB_Sinner_Faust", "The Red Gaze Vergilius", "No Such Identity")));
    }

    @Test
    void imageTakesTheFullArtAndFallsBackToTheIdleSprite() {
        // 파일명은 문서 제목에서 콜론이 빠진 꼴 — 인격 199건 중 197건이 이 둘 중 하나로 걸린다
        assertEquals("https://limbuscompany.wiki.gg/wiki/Special:FilePath/Lobotomy%20E.G.O%20Solemn%20Lament%20Yi%20Sang%20Full.png",
                IdentityCatalog.image("Lobotomy E.G.O::Solemn Lament Yi Sang",
                        "[[File:Lobotomy E.G.O Solemn Lament Yi Sang Full.png]]"));
        assertEquals("https://limbuscompany.wiki.gg/wiki/Special:FilePath/Blade%20Lineage%20Salsu%20Yi%20Sang%20Idle%20Sprite.png",
                IdentityCatalog.image("Blade Lineage Salsu Yi Sang",
                        "[[File:Blade Lineage Salsu Yi Sang Idle Sprite.png]]"));
    }

    @Test
    void imageIsNullWhenTheWikiNamesTheFileSomethingElse() {
        assertNull(IdentityCatalog.image("Cheery Chickies Class Captain Yi Sang", "[[File:YiSang-400025_portrait.png]]"));
        assertNull(IdentityCatalog.image(null, "[[File:Anything Full.png]]"));
    }

    private static Map<String, byte[]> files(String... nameAndJson) {
        Map<String, byte[]> files = new LinkedHashMap<>();
        for (int i = 0; i < nameAndJson.length; i += 2) {
            files.put(nameAndJson[i], nameAndJson[i + 1].getBytes(StandardCharsets.UTF_8));
        }
        return files;
    }
}
