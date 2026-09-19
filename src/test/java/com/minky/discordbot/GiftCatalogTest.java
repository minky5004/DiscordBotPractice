package com.minky.discordbot;

import com.minky.discordbot.GiftCatalog.Gift;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GiftCatalogTest {

    // 위키 Module:EgoGift/data 는 한 줄이 기프트 하나인 Lua 표
    private static final String LUA = """
            return {
            	--MD1 / Mirror of the Beginning
            	["Hellterfly’s Dream"] = { name = "Hellterfly’s Dream", desc = "Inflict {{StatusEffect|Burn|d}}", sin = "wrath", tier = "II", cost = 198, category = "Burn", upgrade = 2 },
            	["Hellterfly’s Dream+"] = { name = "Hellterfly’s Dream", desc = "+", sin = "wrath", tier = "II", cost = 198, category = "Burn", imgname = "Hellterfly's Dream" },
            	["Hot 'n Juicy Drumstick"] = { name = "Hot 'n Juicy Drumstick", imgname = "Juicy Drumstick", desc = "Say \\"hi\\"", sin = "gluttony" },
            }""";

    @Test
    void wikiDataReadsOneEntryPerLineWithEscapedQuotes() {
        Map<String, Map<String, String>> data = GiftCatalog.wikiData(LUA);

        assertEquals(3, data.size());
        assertEquals(Map.of("name", "Hellterfly’s Dream", "desc", "Inflict {{StatusEffect|Burn|d}}", "sin", "wrath",
                "tier", "II", "cost", "198", "category", "Burn", "upgrade", "2"), data.get("Hellterfly’s Dream"));
        assertEquals("Say \"hi\"", data.get("Hot 'n Juicy Drumstick").get("desc"));
    }

    @Test
    void upgradeHighlightBecomesBoldBeforeTheRestOfTheMarkupIsStripped() {
        assertEquals("모든 적에게 **화상 위력 4** 부여 <혈귀>",
                GiftCatalog.giftText("모든 적에게 <style=\"upgradeHighlight\">[Combustion] 위력 4</style> 부여 <혈귀>",
                        Map.of("Combustion", "화상")));
    }

    @Test
    void buildJoinsMirrorDungeonGiftsWithWikiDataByEnglishName() {
        Map<String, byte[]> files = files(
                "KR_EGOgift_MirrorDungeon.json", """
                        {"dataList":[
                          {"id":9001,"name":"지옥나비의 꿈","desc":"[Combustion] 3 부여"},
                          {"id":19001,"name":"지옥나비의 꿈+","desc":"<style=\\"upgradeHighlight\\">[Combustion] 4</style> 부여"},
                          {"id":29001,"name":"지옥나비의 꿈++","desc":"<style=\\"upgradeHighlight\\">[Combustion] 5</style> 부여"}]}""",
                "KR_EGOgift_MirrorDungeon_7.json", """
                        {"dataList":[{"id":9701,"name":"뜨거운 육즙 다리살","desc":"회복"}]}""",
                // 스토리 던전 기프트는 대상 밖
                "KR_EGOgift_StoryDungeon.json", """
                        {"dataList":[{"id":8001,"name":"스토리 기프트","desc":"스토리"}]}""",
                "EN_EGOgift_MirrorDungeon.json", """
                        {"dataList":[{"id":9001,"name":"Hellterfly's Dream"},{"id":9701,"name":"Hot ‘n Juicy Drumstick"},{"id":8001,"name":"Story"}]}""",
                "KR_Bufs.json", """
                        {"dataList":[{"id":"Combustion","name":"화상"}]}""");

        List<String> requested = new ArrayList<>();
        List<Gift> gifts = GiftCatalog.build(files, titles -> {
            requested.addAll(titles);
            return Map.of(GiftCatalog.WIKI_DATA, LUA);
        });

        assertEquals(List.of(GiftCatalog.WIKI_DATA), requested);
        assertEquals(List.of(
                        // 게임 쪽 ' 와 위키 쪽 ’ 가 섞여도 짝 · 그림은 imgname 우선
                        new Gift(9001, "지옥나비의 꿈", "분노", "II", 198, "화상", true,
                                "https://limbuscompany.wiki.gg/wiki/Special:FilePath/Hellterfly%E2%80%99s%20Dream%20Gift.png",
                                List.of("화상 3 부여", "**화상 4** 부여", "**화상 5** 부여")),
                        new Gift(9701, "뜨거운 육즙 다리살", "탐식", null, null, null, true,
                                "https://limbuscompany.wiki.gg/wiki/Special:FilePath/Juicy%20Drumstick%20Gift.png",
                                List.of("회복"))),
                gifts);
    }

    @Test
    void giftWithoutWikiDataKeepsGameTextOnly() {
        Map<String, byte[]> files = files(
                "KR_EGOgift_MirrorDungeon.json", """
                        {"dataList":[{"id":9001,"name":"지옥나비의 꿈","desc":"설명"}]}""");

        assertEquals(List.of(new Gift(9001, "지옥나비의 꿈", null, null, null, null, false, null, List.of("설명"))),
                GiftCatalog.build(files, titles -> Map.of()));
    }

    private static Map<String, byte[]> files(String... nameAndJson) {
        Map<String, byte[]> files = new LinkedHashMap<>();
        for (int i = 0; i < nameAndJson.length; i += 2) {
            files.put(nameAndJson[i], nameAndJson[i + 1].getBytes(StandardCharsets.UTF_8));
        }
        return files;
    }
}
