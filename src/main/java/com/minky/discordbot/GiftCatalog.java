package com.minky.discordbot;

import net.dv8tion.jda.api.utils.data.DataObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// 설치된 게임의 거울 던전 E.G.O 기프트 텍스트에 위키 데이터 모듈의 죄악 · 등급 · 가격 · 키워드를 붙인 목록
class GiftCatalog {

    // texts 는 기본 · + · ++ 순 · 위키 칸은 없으면 null · wiki 는 위키 데이터에서 짝을 찾았는지
    record Gift(int id, String name, String sin, String tier, Integer cost, String keyword, boolean wiki, String image,
                List<String> texts) {
    }

    // 기프트마다 문서가 있는 게 아니라 모듈 한 페이지에 전부 · 요청 한 번
    static final String WIKI_DATA = "Module:EgoGift/data";

    // 스토리 · 이벤트 던전 기프트는 대상 밖
    private static final String GIFT_FILE = "KR_EGOgift_MirrorDungeon";

    private static final String EN_GIFT_FILE = "EN_EGOgift";

    // 강화판은 기본 ID + 10000 (+) · + 20000 (++)
    private static final int UPGRADE_STEP = 10000;

    private static final int MAX_UPGRADES = 2;

    private static final Map<String, String> KEYWORDS = Map.of(
            "burn", "화상", "bleed", "출혈", "tremor", "진동", "rupture", "파열", "sinking", "침잠",
            "poise", "호흡", "charge", "충전", "slash", "참격", "pierce", "관통", "blunt", "타격");

    private static final String STRING = "\"((?:[^\"\\\\]|\\\\.)*)\"";

    // ["키"] = { 필드 = 값, ... } 한 줄
    private static final Pattern ENTRY = Pattern.compile("^\\s*\\[" + STRING + "]\\s*=\\s*\\{(.*)}\\s*,?\\s*$", Pattern.MULTILINE);

    private static final Pattern FIELD = Pattern.compile("(\\w+)\\s*=\\s*(?:" + STRING + "|(-?\\d+))");

    private static final Pattern ESCAPE = Pattern.compile("\\\\(.)");

    // 강화판에서 바뀐 수치 · 다른 마크업을 지우기 전에 굵게로 바꾼다
    private static final Pattern HIGHLIGHT = Pattern.compile("<style=\"upgradeHighlight\">(.*?)</style>", Pattern.DOTALL);

    static List<Gift> build(Map<String, byte[]> files, Function<List<String>, Map<String, String>> wiki) {
        Map<Integer, DataObject> gifts = IdentityCatalog.rows(files, GIFT_FILE);
        Map<Integer, DataObject> enGifts = IdentityCatalog.rows(files, EN_GIFT_FILE);
        Map<String, String> tags = IdentityCatalog.names(files, "KR_Bufs");
        tags.putAll(IdentityCatalog.names(files, "KR_SkillTag"));

        String lua = wiki.apply(List.of(WIKI_DATA)).get(WIKI_DATA);
        Map<String, Map<String, String>> data = new HashMap<>();
        if (lua != null) {
            wikiData(lua).forEach((key, fields) -> data.put(key(key), fields));
        }

        List<Gift> result = new ArrayList<>();
        gifts.forEach((id, kr) -> {
            if (id >= UPGRADE_STEP) {
                return;
            }
            List<String> texts = new ArrayList<>();
            texts.add(giftText(kr.getString("desc", ""), tags));
            for (int level = 1; level <= MAX_UPGRADES && gifts.containsKey(id + level * UPGRADE_STEP); level++) {
                texts.add(giftText(gifts.get(id + level * UPGRADE_STEP).getString("desc", ""), tags));
            }
            DataObject en = enGifts.get(id);
            Map<String, String> fields = en == null ? null : data.get(key(en.getString("name", "")));
            result.add(fields == null
                    ? new Gift(id, kr.getString("name", ""), null, null, null, null, false, null, texts)
                    : new Gift(id, kr.getString("name", ""), IdentityCatalog.SINS.get(IdentityCatalog.lower(fields.get("sin"))),
                    blankToNull(fields.get("tier")), IdentityCatalog.integer(fields.get("cost")),
                    KEYWORDS.get(IdentityCatalog.lower(fields.get("category"))), true,
                    // 파일 이름은 imgname 우선 · 모듈이 따옴표 모양이 다른 파일을 이 칸으로 가리킨다
                    IdentityCatalog.fileUrl(fields.getOrDefault("imgname", fields.get("name")), " Gift.png"), texts));
        });
        return result;
    }

    // 모듈은 한 줄이 기프트 하나 · Lua 문법 전체가 아니라 이 모양만 읽는다
    static Map<String, Map<String, String>> wikiData(String lua) {
        Map<String, Map<String, String>> data = new LinkedHashMap<>();
        Matcher entry = ENTRY.matcher(lua);
        while (entry.find()) {
            Map<String, String> fields = new LinkedHashMap<>();
            Matcher field = FIELD.matcher(entry.group(2));
            while (field.find()) {
                fields.put(field.group(1), field.group(2) != null ? unescape(field.group(2)) : field.group(3));
            }
            data.put(unescape(entry.group(1)), fields);
        }
        return data;
    }

    static String giftText(String desc, Map<String, String> tags) {
        String bold = HIGHLIGHT.matcher(desc).replaceAll(match -> Matcher.quoteReplacement("**" + match.group(1) + "**"));
        return IdentityCatalog.richText(bold, tags).strip();
    }

    // 게임 · 위키가 따옴표 모양을 제각각 쓴다 (Hot ‘n · Hellterfly’s)
    private static String key(String name) {
        return name.replace('’', '\'').replace('‘', '\'').strip();
    }

    private static String unescape(String value) {
        return ESCAPE.matcher(value).replaceAll(match -> Matcher.quoteReplacement(match.group(1)));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
