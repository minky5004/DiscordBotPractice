package com.minky.discordbot;

import com.minky.discordbot.EgoCatalog.Ego;
import com.minky.discordbot.EgoCatalog.EgoSkill;
import com.minky.discordbot.IdentityCatalog.Passive;
import com.minky.discordbot.IdentityCatalog.SkillStats;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EgoCatalogTest {

    @Test
    void wikiTitleUsesStraightQuotesAndDropsHanjaBrackets() {
        assertEquals("Wishing Cairn Yi Sang", EgoCatalog.wikiTitle("Wishing Cairn", "Yi Sang"));
        assertEquals("Crow's Eye View Yi Sang", EgoCatalog.wikiTitle("Crow’s Eye View", "Yi Sang"));
        assertEquals("Great Trichiliocosm 三千大世界 Ryōshū", EgoCatalog.wikiTitle("Great Trichiliocosm [三千大世界]", "Ryōshū"));
    }

    @Test
    void buildJoinsGameTextWithWikiStatsBySlotAndName() {
        Map<String, byte[]> files = files(
                "KR_Personalities.json", """
                        {"dataList":[{"id":10101,"title":"LCB 수감자","name":"이상"},{"id":10601,"title":"LCB 수감자","name":"싱클레어"}]}""",
                "EN_Personalities.json", """
                        {"dataList":[{"id":10101,"title":"LCB Sinner","name":"Yi Sang"},{"id":10601,"title":"LCB Sinner","name":"Sinclair"}]}""",
                "KR_Egos.json", """
                        {"dataList":[
                          {"id":201011,"name":"오감도","desc":"연출 전용"},
                          {"id":20103,"name":"소망석"},
                          {"id":20608,"name":"오혈읍루 [汚血泣淚]"},
                          {"id":20610,"name":"거절선포"}]}""",
                "EN_Egos.json", """
                        {"dataList":[
                          {"id":20103,"name":"Wishing Cairn"},
                          {"id":20608,"name":"Tears of the Tarnished Blood [汚血泣淚]"},
                          {"id":20610,"name":"Repudiation"}]}""",
                "KR_Skills_Ego_Personality-01.json", """
                        {"dataList":[
                          {"id":2010311,"levelList":[{"level":1,"name":"낮은 단계"},{"level":5,"name":"소망석","desc":"가장 뒤 대상 공격",
                            "coinlist":[{"coindescs":[{"desc":"[Paralysis] 3 부여"}]}]}]},
                          {"id":2010321,"levelList":[{"level":4,"name":"소망석","desc":"무작위 대상 공격"}]},
                          {"id":2060811,"levelList":[{"level":4,"name":"오혈읍루 - 시[始]","desc":"시작"}]},
                          {"id":2060812,"levelList":[{"level":4,"name":"오혈읍루 - 종[終]","desc":"끝"}]}]}""",
                "EN_Skills_Ego_Personality-01.json", """
                        {"dataList":[
                          {"id":2010311,"levelList":[{"level":5,"name":"Wishing Cairn"}]},
                          {"id":2010321,"levelList":[{"level":4,"name":"Wishing Cairn"}]},
                          {"id":2060811,"levelList":[{"level":4,"name":"Tears of the Tarnished Blood - Inception [始]"}]},
                          {"id":2060812,"levelList":[{"level":4,"name":"Tears of the Tarnished Blood - The End [終]"}]}]}""",
                "KR_Passive_Ego.json", """
                        {"dataList":[{"id":2010311,"name":"돌하르방","desc":"매 턴 [Paralysis] 면역"}]}""",
                "KR_Bufs.json", """
                        {"dataList":[{"id":"Paralysis","name":"마비"}]}""");
        String cairn = """
                {{EGPage
                |risk=TETH
                |asanity=20
                |csanity=15
                |slothcost=4
                |gloomcost=1
                |wrathcost=
                |wrathres=Normal
                |slothres=Ineff
                |gloomres=Fatal
                |askill={{Skill
                |sin=Sloth
                |name=Wishing Cairn
                |type=Pierce
                |icon=Wishing Cairn Yi Sang Icon
                |spower=22
                |cpower=+4
                |coin=1
                }}
                |askill5={{Skill
                |sin=Sloth
                |name=Wishing Cairn
                |type=Pierce
                |spower=24
                |cpower=+5
                |coin=1
                }}
                |cskill={{Skill
                |sin=Sloth
                |name=Wishing Cairn
                |type=Blunt
                |spower=29
                |cpower=-10
                |coin=1
                }}
                }}""";
        String tears = """
                {{EGPage
                |risk=WAW
                |askill={{Skill
                |sin=Gluttony
                |name=Tears of the Tarnished Blood
                |type=Pierce
                |spower=8
                |cpower=+ 6
                |coin=2
                }}
                }}""";

        List<String> requested = new ArrayList<>();
        List<Ego> egos = EgoCatalog.build(files, titles -> {
            requested.addAll(titles);
            return Map.of("Wishing Cairn Yi Sang", cairn, "Tears of the Tarnished Blood 汚血泣淚 Sinclair", tears);
        });

        // 스킬 · 패시브가 하나도 없는 E.G.O(출시 전 데이터) · 여섯 자리 연출 전용 장비는 목록에서 뺀다
        assertEquals(List.of("Wishing Cairn Yi Sang", "Tears of the Tarnished Blood 汚血泣淚 Sinclair"), requested);
        assertEquals(List.of(
                        new Ego(20103, "소망석", "이상", "TETH",
                                "https://limbuscompany.wiki.gg/wiki/Special:FilePath/Wishing%20Cairn%20Yi%20Sang.png",
                                Map.of("나태", 4, "우울", 1), Map.of("분노", 1.0, "나태", 0.5, "우울", 2.0),
                                List.of(
                                        // 게임 최고 단계가 5 면 위키의 5단계 칸
                                        new EgoSkill(2010311, false, "소망석", "가장 뒤 대상 공격\n코인1 마비 3 부여",
                                                new SkillStats("나태", "관통", 24, "+5", 1, null), 20),
                                        new EgoSkill(2010321, true, "소망석", "무작위 대상 공격",
                                                new SkillStats("나태", "타격", 29, "-10", 1, null), 15)),
                                List.of(new Passive(2010311, "돌하르방", "매 턴 마비 면역", false, null))),
                        new Ego(20608, "오혈읍루 [汚血泣淚]", "싱클레어", "WAW",
                                "https://limbuscompany.wiki.gg/wiki/Special:FilePath/Tears%20of%20the%20Tarnished%20Blood%20%E6%B1%9A%E8%A1%80%E6%B3%A3%E6%B7%9A%20Sinclair.png",
                                Map.of(), Map.of(),
                                List.of(
                                        // 이름이 어긋나도 기본 스킬은 그 칸의 첫 블록 · 두 번째 각성 스킬은 수치 없이
                                        new EgoSkill(2060811, false, "오혈읍루 - 시[始]", "시작",
                                                new SkillStats("탐식", "관통", 8, "+6", 2, null), null),
                                        new EgoSkill(2060812, false, "오혈읍루 - 종[終]", "끝", null, null)),
                                List.of())),
                egos);
    }

    @Test
    void egoWithoutWikiPageKeepsGameTextOnly() {
        Map<String, byte[]> files = files(
                "KR_Personalities.json", """
                        {"dataList":[{"id":10101,"title":"LCB 수감자","name":"이상"}]}""",
                "KR_Egos.json", """
                        {"dataList":[{"id":20103,"name":"소망석"}]}""",
                "KR_Skills_Ego.json", """
                        {"dataList":[{"id":2010311,"levelList":[{"level":4,"name":"소망석","desc":"공격"}]}]}""");

        assertEquals(List.of(new Ego(20103, "소망석", "이상", null, null, Map.of(), Map.of(),
                        List.of(new EgoSkill(2010311, false, "소망석", "공격", null, null)), List.of())),
                EgoCatalog.build(files, titles -> Map.of()));
    }

    private static Map<String, byte[]> files(String... nameAndJson) {
        Map<String, byte[]> files = new LinkedHashMap<>();
        for (int i = 0; i < nameAndJson.length; i += 2) {
            files.put(nameAndJson[i], nameAndJson[i + 1].getBytes(StandardCharsets.UTF_8));
        }
        return files;
    }
}
