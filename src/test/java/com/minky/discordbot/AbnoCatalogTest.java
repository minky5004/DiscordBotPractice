package com.minky.discordbot;

import com.minky.discordbot.AbnoCatalog.Abno;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AbnoCatalogTest {

    private static final String PUNISHING_BIRD = """
            {{AbnoInfo
            | title=Punishing Bird
            | image=Punishing Bird LobCorp.png
            | krtitle=징벌 새
            | code=O-02-56
            | risk=TETH
            | ego=Beak
            | location=[[Mirror of Names and Spiders]] (The Dusk of Amber)
            | egogift=Beak-shaped Necklace
            | egogift2=Punishing Bird's Beak
            }}
            '''Punishing Bird''' (O-02-56) is a TETH-class Abnormality.""";

    // 도감 코드에 형태 접미사가 붙는 환상체 · 로마자 병기는 krtitle 의 <br> 뒤에 온다
    private static final String BABA_YAGA = """
            {{AbnoInfo
            | title=Baba Yaga
            | image=Baba_Yaga_Castle.png
            | krtitle=바바야가<br>(''ba-ba-ya-ga'')
            | code=F-02-10-18
            | ego=Sloshing
            }}""";

    private static Map<String, byte[]> files(String... nameAndJson) {
        Map<String, byte[]> files = new LinkedHashMap<>();
        for (int i = 0; i < nameAndJson.length; i += 2) {
            files.put(nameAndJson[i], nameAndJson[i + 1].getBytes(StandardCharsets.UTF_8));
        }
        return files;
    }

    private static Map<String, byte[]> game() {
        return files(
                "KR_AbnormalityGuides.json", """
                        {"dataList":[
                          {"id":8001,"codeName":"O-02-56","name":"징벌 새","clue":"단서",
                           "storyList":[{"level":0,"story":"작은 새다."},{"level":1,"story":"성나면 붉어진다."}]},
                          {"id":8002,"codeName":"O-02-56","name":"징벌 새","clue":"단서",
                           "storyList":[{"level":0,"story":"거울 던전 사본"}]}]}""",
                // 스토리 챕터 파일은 같은 코드를 더 작은 ID 로 · 관찰 로그가 아니라 작업 결과 메모 한 줄
                "KR_AbnormalityGuides-a1c9p1.json", """
                        {"dataList":[{"id":1321,"codeName":"O-02-56","name":"징벌 새",
                          "storyList":[{"level":0,"story":"작업 결과가 나쁨일 때"}]}]}""",
                // 난이도 변형 파일도 같은 코드를 쓴다
                "KR_AbnormalityGuides_Mirror.json", """
                        {"dataList":[{"id":8701,"codeName":"F-02-10-18-a","name":"바바야가-a",
                          "storyList":[{"level":0,"story":"집이 걸어다닌다."}]}]}""",
                "KR_Egos.json", """
                        {"dataList":[{"id":20101,"name":"부리"}]}""",
                "EN_Egos.json", """
                        {"dataList":[{"id":20101,"name":"Beak"}]}""",
                "KR_EGOgift_MirrorDungeon.json", """
                        {"dataList":[{"id":9001,"name":"부리 모양 목걸이"}]}""",
                "EN_EGOgift_MirrorDungeon.json", """
                        {"dataList":[{"id":9001,"name":"Beak-shaped Necklace"}]}""");
    }

    @Test
    void buildJoinsWikiInfoWithTheKoreanGuideLogsOfTheSameCode() {
        List<String> requested = new ArrayList<>();
        List<Abno> abnos = AbnoCatalog.build(game(), List.of("Punishing Bird"), titles -> {
            requested.addAll(titles);
            return Map.of("Punishing Bird", PUNISHING_BIRD);
        });

        assertEquals(List.of("Punishing Bird"), requested);
        assertEquals(List.of(new Abno("Punishing Bird", "징벌 새", "O-02-56", "TETH", "부리",
                        // 게임에 없는 기프트는 위키의 영어 이름 그대로 · 로그는 같은 코드의 행 중 가장 많은 쪽
                        List.of("부리 모양 목걸이", "Punishing Bird's Beak"),
                        "Mirror of Names and Spiders (The Dusk of Amber)",
                        "https://limbuscompany.wiki.gg/wiki/Special:FilePath/Punishing%20Bird%20LobCorp.png",
                        List.of("작은 새다.", "성나면 붉어진다."))),
                abnos);
    }

    @Test
    void guideCodeWithAFormSuffixStillCarriesItsLogs() {
        List<Abno> abnos = AbnoCatalog.build(game(), List.of("Baba Yaga"), titles -> Map.of("Baba Yaga", BABA_YAGA));

        Abno babaYaga = abnos.getFirst();
        assertEquals("바바야가", babaYaga.name());
        assertEquals(List.of("집이 걸어다닌다."), babaYaga.logs());
        // 게임에 없는 E.G.O 는 위키의 영어 이름 · 빈 칸은 null
        assertEquals("Sloshing", babaYaga.ego());
        assertNull(babaYaga.risk());
        assertEquals(List.of(), babaYaga.gifts());
    }

    @Test
    void abnormalityWithoutAGuideEntryKeepsWikiInfoOnly() {
        List<Abno> abnos = AbnoCatalog.build(files(), List.of("Punishing Bird"),
                titles -> Map.of("Punishing Bird", PUNISHING_BIRD));

        assertEquals(List.of(), abnos.getFirst().logs());
        assertEquals("Beak", abnos.getFirst().ego());
    }

    @Test
    void emptyKoreanTitleFallsBackToTheDocumentTitleInsteadOfBreakingTheList() {
        // 값 없는 칸도 빈 문자열로 담긴다 · 이름이 null 이면 정렬이 목록 전체를 떨어뜨린다
        List<Abno> abnos = AbnoCatalog.build(files(), List.of("Unreleased"),
                titles -> Map.of("Unreleased", """
                        {{AbnoInfo
                        | title=Unreleased
                        | krtitle=
                        }}"""));

        assertEquals("Unreleased", abnos.getFirst().name());
    }

    @Test
    void fieldStartingWithATagKeepsItsFirstLine() {
        // image 칸이 <gallery> 로 시작하는 문서가 있다
        assertEquals("Golden Apple Idle Sprite.png",
                AbnoCatalog.plain("""
                        <gallery>
                        Golden Apple Idle Sprite.png
                        Golden Apple.png
                        </gallery>"""));
        // 로마자 병기는 <br> 뒤라 첫 줄만
        assertEquals("바바야가", AbnoCatalog.plain("바바야가<br>(''ba-ba-ya-ga'')"));
    }

    @Test
    void pageWithoutTheTemplateIsLeftOutAndTheRestIsSortedByKoreanName() {
        List<Abno> abnos = AbnoCatalog.build(game(), List.of("Punishing Bird", "Baba Yaga", "Redirect"),
                titles -> Map.of("Punishing Bird", PUNISHING_BIRD, "Baba Yaga", BABA_YAGA,
                        "Redirect", "#REDIRECT [[Punishing Bird]]"));

        assertEquals(List.of("바바야가", "징벌 새"), abnos.stream().map(Abno::name).toList());
    }
}
