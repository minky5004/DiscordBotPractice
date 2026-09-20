package com.minky.discordbot;

import net.dv8tion.jda.api.utils.data.DataArray;
import net.dv8tion.jda.api.utils.data.DataObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.regex.Pattern;

// 위키(wiki.gg) {{AbnoInfo}} 문서에 설치된 게임의 한국어 관찰 로그를 붙인 환상체 목록 · 갱신은 IdentityCatalog 가 같은 파일로 함께
class AbnoCatalog {

    // level 은 관찰 단계 · 게임 도감에 없는 환상체면 목록 자체가 빈다
    record Log(int level, String text) {
    }

    // ego · gifts 는 게임 텍스트에서 한국어 이름을 찾지 못하면 위키의 영어 이름 그대로 · 나머지 위키 칸은 없으면 null
    record Abno(String title, String name, String code, String risk, String ego, List<String> gifts, String location,
                String image, List<Log> logs) {
    }

    // 환상체마다 문서가 있으므로 목록은 이 틀을 쓰는 문서로 받는다
    static final String WIKI_TEMPLATE = "AbnoInfo";

    private static final String GUIDE_FILE = "KR_AbnormalityGuides";

    private static final List<String> GIFT_SLOTS = List.of("egogift", "egogift2", "egogift3");

    private static final Pattern LINK = Pattern.compile("\\[\\[(?:[^|\\]]*\\|)?([^\\]]*)]]");

    private static final Pattern TAG = Pattern.compile("<[^>]*>");

    static List<Abno> build(Map<String, byte[]> files, List<String> titles,
                            Function<List<String>, Map<String, String>> wiki) {
        Map<String, String> pages = wiki.apply(titles);
        Map<String, String> egos = korean(files, "EN_Egos", "KR_Egos");
        Map<String, String> gifts = korean(files, "EN_EGOgift", "KR_EGOgift");
        Map<String, DataObject> guides = guides(files);

        List<Abno> result = new ArrayList<>();
        for (String title : titles) {
            String page = pages.get(title);
            Map<String, String> info = page == null ? null
                    : IdentityCatalog.templates(page, WIKI_TEMPLATE).stream().findFirst().orElse(null);
            if (info == null) {
                continue;
            }
            String code = plain(info.get("code"));
            List<String> giftNames = GIFT_SLOTS.stream()
                    .map(slot -> korean(gifts, plain(info.get(slot))))
                    .filter(name -> name != null)
                    .toList();
            result.add(new Abno(title, plain(info.getOrDefault("krtitle", title)), code, plain(info.get("risk")),
                    korean(egos, plain(info.get("ego"))), giftNames, plain(info.get("location")),
                    IdentityCatalog.fileUrl(plain(info.get("image")), ""), logs(guide(guides, code))));
        }
        result.sort(Comparator.comparing(Abno::name));
        return result;
    }

    // 게임에 없는 E.G.O · 기프트는 위키 문서에만 있는 미출시분이라 영어 이름을 그대로 둔다
    static String korean(Map<String, String> names, String english) {
        if (english == null || english.isBlank()) {
            return null;
        }
        return names.getOrDefault(IdentityCatalog.nameKey(english), english);
    }

    // 같은 ID 의 영어 · 한국어 행을 짝지어 영어 이름으로 한국어 이름을 찾는 표
    private static Map<String, String> korean(Map<String, byte[]> files, String enPrefix, String krPrefix) {
        Map<Integer, DataObject> kr = IdentityCatalog.rows(files, krPrefix);
        Map<String, String> names = new LinkedHashMap<>();
        IdentityCatalog.rows(files, enPrefix).forEach((id, en) -> {
            DataObject match = kr.get(id);
            if (match != null && en.hasKey("name") && match.hasKey("name")) {
                names.putIfAbsent(IdentityCatalog.nameKey(en.getString("name")), match.getString("name"));
            }
        });
        return names;
    }

    // 한 환상체가 난이도 · 거울 던전 변형마다 같은 내용으로 여러 행 · 코드마다 첫 행만
    private static Map<String, DataObject> guides(Map<String, byte[]> files) {
        Map<String, DataObject> byCode = new TreeMap<>();
        IdentityCatalog.rows(files, GUIDE_FILE).values().forEach(row -> {
            String code = row.getString("codeName", "").strip();
            if (!code.isEmpty() && !row.optArray("storyList").orElseGet(DataArray::empty).isEmpty()) {
                byCode.putIfAbsent(code, row);
            }
        });
        return byCode;
    }

    // 도감 코드가 위키 코드에 형태 접미사를 붙여 쓰기도 한다 (바바야가 F-02-10-18 · 도감 F-02-10-18-a)
    static DataObject guide(Map<String, DataObject> guides, String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        DataObject exact = guides.get(code);
        if (exact != null) {
            return exact;
        }
        return guides.entrySet().stream()
                .filter(entry -> entry.getKey().startsWith(code))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    static List<Log> logs(DataObject guide) {
        if (guide == null) {
            return List.of();
        }
        DataArray list = guide.optArray("storyList").orElseGet(DataArray::empty);
        List<Log> logs = new ArrayList<>();
        for (int i = 0; i < list.length(); i++) {
            DataObject row = list.getObject(i);
            String story = row.getString("story", "").strip();
            if (!story.isEmpty()) {
                logs.add(new Log(row.getInt("level", i), story));
            }
        }
        return logs;
    }

    // 위키 칸에는 문서 링크 · 로마자 병기 · 기울임 마크업이 섞인다. 병기는 <br> 뒤라 첫 줄만 남긴다.
    static String plain(String value) {
        if (value == null) {
            return null;
        }
        String text = TAG.split(value, 2)[0];
        text = LINK.matcher(text).replaceAll("$1").replace("''", "").strip();
        return text.isEmpty() ? null : text;
    }
}
