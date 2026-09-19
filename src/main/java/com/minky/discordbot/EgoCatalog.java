package com.minky.discordbot;

import com.minky.discordbot.IdentityCatalog.Passive;
import com.minky.discordbot.IdentityCatalog.SkillStats;
import net.dv8tion.jda.api.utils.data.DataObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;

// 설치된 게임의 E.G.O 텍스트에 위키(wiki.gg) {{EGPage}} 수치를 붙인 목록 · 갱신은 IdentityCatalog 가 같은 파일로 함께
class EgoCatalog {

    // corrosion 이 거짓이면 각성 · sanity 는 정신력 소모 · stats 와 sanity 는 위키에서 짝을 못 찾으면 null
    record EgoSkill(int id, boolean corrosion, String name, String text, SkillStats stats, Integer sanity) {
    }

    // risk · image 는 위키 문서가 없으면 null · costs 와 resists 는 죄악 순서(분노 → 질투)
    record Ego(int id, String name, String sinner, String risk, String image, Map<String, Integer> costs,
               Map<String, Double> resists, List<EgoSkill> skills, List<Passive> passives) {
    }

    // 위키 파라미터 접두 순서가 게임 화면의 죄악 순서
    private static final List<String> SIN_ORDER = List.of("wrath", "lust", "sloth", "gluttony", "gloom", "pride", "envy");

    // E.G.O 는 2SSNN · SS 가 수감자 번호(인격 1SSNN 과 같은 자리) · 201011 같은 여섯 자리는 연출 전용 장비
    static boolean isEgo(int id) {
        return id >= 20000 && id < 30000;
    }

    static int sinnerNumber(int id) {
        return id / 100 % 100;
    }

    static List<Ego> build(Map<String, byte[]> files, Function<List<String>, Map<String, String>> wiki) {
        Map<Integer, String> sinners = sinners(files, "KR_Personalities");
        Map<Integer, String> enSinners = sinners(files, "EN_Personalities");
        Map<Integer, DataObject> egos = IdentityCatalog.rows(files, "KR_Egos");
        egos.keySet().removeIf(id -> !isEgo(id));
        Map<Integer, DataObject> enEgos = IdentityCatalog.rows(files, "EN_Egos");
        // KR_Skills · KR_Passive 접두어가 인격 파일까지 잡는다 · 주인 ID 로 거른다
        Map<Integer, DataObject> skills = IdentityCatalog.rows(files, "KR_Skills");
        Map<Integer, DataObject> enSkills = IdentityCatalog.rows(files, "EN_Skills");
        Map<Integer, DataObject> passives = IdentityCatalog.rows(files, "KR_Passive");
        Map<String, String> tags = IdentityCatalog.names(files, "KR_Bufs");
        tags.putAll(IdentityCatalog.names(files, "KR_SkillTag"));

        // 스킬 · 패시브가 하나도 없는 E.G.O 는 출시 전 데이터(위키에도 문서 없음)
        egos.keySet().removeIf(id -> skills.keySet().stream().noneMatch(skillId -> skillId / 100 == id)
                && passives.keySet().stream().noneMatch(passiveId -> passiveId / 100 == id));

        Map<Integer, String> titles = new TreeMap<>();
        egos.keySet().forEach(id -> {
            DataObject en = enEgos.get(id);
            String sinner = enSinners.get(sinnerNumber(id));
            if (en != null && sinner != null) {
                titles.put(id, wikiTitle(en.getString("name", ""), sinner));
            }
        });
        Map<String, String> pages = wiki.apply(List.copyOf(titles.values()));

        List<Ego> result = new ArrayList<>();
        egos.forEach((id, kr) -> {
            String page = pages.get(titles.getOrDefault(id, ""));
            Map<String, String> egoPage = page == null ? null
                    : IdentityCatalog.templates(page, "EGPage").stream().findFirst().orElse(null);

            List<EgoSkill> egoSkills = new ArrayList<>();
            skills.forEach((skillId, skill) -> {
                if (skillId / 100 != id) {
                    return;
                }
                DataObject top = IdentityCatalog.lastLevel(skill);
                if (top == null) {
                    return;
                }
                // 끝번호 11 · 12 가 각성, 21 이 침식
                boolean corrosion = skillId % 100 / 10 == 2;
                Map<String, String> stats = egoPage == null ? null
                        : wikiSkill(egoPage, corrosion, top.getInt("level", 0),
                        IdentityCatalog.englishSkillName(enSkills.get(skillId)), skillId % 10 == 1);
                egoSkills.add(new EgoSkill(skillId, corrosion, top.getString("name", ""), IdentityCatalog.skillText(top, tags),
                        stats == null ? null : IdentityCatalog.skillStats(stats),
                        egoPage == null ? null : IdentityCatalog.integer(egoPage.get(corrosion ? "csanity" : "asanity"))));
            });

            List<Passive> egoPassives = new ArrayList<>();
            passives.forEach((passiveId, passive) -> {
                if (passiveId / 100 == id) {
                    egoPassives.add(new Passive(passiveId, passive.getString("name", ""),
                            IdentityCatalog.richText(passive.getString("desc", ""), tags), false, null));
                }
            });

            result.add(new Ego(id, kr.getString("name", ""), sinners.getOrDefault(sinnerNumber(id), ""),
                    egoPage == null ? null : blankToNull(egoPage.get("risk")),
                    egoPage == null ? null : IdentityCatalog.fileUrl(titles.get(id), ".png"),
                    egoPage == null ? Map.of() : costs(egoPage), egoPage == null ? Map.of() : resists(egoPage),
                    egoSkills, egoPassives));
        });
        return result;
    }

    // 위키 문서 제목 · 곧은 따옴표 · 한자 병기는 괄호만 벗긴다(Great Trichiliocosm 三千大世界 Ryōshū)
    static String wikiTitle(String name, String sinner) {
        return IdentityCatalog.oneLine(name + " " + sinner)
                .replace('’', '\'')
                .replace("[", "").replace("]", "")
                .replaceAll("\\s+", " ").strip();
    }

    // 게임 최고 단계가 5 인 스킬은 위키의 5단계 칸(askill5) · 나머지는 번호 없는 칸
    // 이름이 어긋나면 기본 스킬(끝번호 1)만 그 칸의 첫 블록 · 두 번째 각성 스킬에 첫 스킬 수치를 붙이지 않는다
    private static Map<String, String> wikiSkill(Map<String, String> page, boolean corrosion, int level, String englishName,
                                                 boolean primary) {
        String slot = corrosion ? "cskill" : "askill";
        String value = level >= 5 && page.containsKey(slot + "5") ? page.get(slot + "5") : page.get(slot);
        if (value == null) {
            return null;
        }
        List<Map<String, String>> blocks = IdentityCatalog.templates(value, "Skill");
        String key = IdentityCatalog.nameKey(englishName);
        return blocks.stream()
                .filter(block -> IdentityCatalog.nameKey(block.getOrDefault("name", "")).equals(key))
                .findFirst()
                .orElse(primary && !blocks.isEmpty() ? blocks.getFirst() : null);
    }

    private static Map<String, Integer> costs(Map<String, String> page) {
        Map<String, Integer> costs = new LinkedHashMap<>();
        for (String sin : SIN_ORDER) {
            Integer cost = IdentityCatalog.integer(page.get(sin + "cost"));
            if (cost != null && cost > 0) {
                costs.put(IdentityCatalog.SINS.get(sin), cost);
            }
        }
        return costs;
    }

    private static Map<String, Double> resists(Map<String, String> page) {
        Map<String, Double> resists = new LinkedHashMap<>();
        for (String sin : SIN_ORDER) {
            Double resist = page.containsKey(sin + "res") ? IdentityCatalog.resist(page.get(sin + "res")) : null;
            if (resist != null) {
                resists.put(IdentityCatalog.SINS.get(sin), resist);
            }
        }
        return resists;
    }

    // 수감자 번호 → 이름 · 인격 1SSNN 에서
    private static Map<Integer, String> sinners(Map<String, byte[]> files, String prefix) {
        Map<Integer, String> sinners = new HashMap<>();
        IdentityCatalog.rows(files, prefix).forEach((id, row) -> {
            if (IdentityCatalog.isIdentity(id)) {
                sinners.putIfAbsent(sinnerNumber(id), row.getString("name", ""));
            }
        });
        return sinners;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
