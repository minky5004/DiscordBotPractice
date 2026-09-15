package com.minky.discordbot;

import net.dv8tion.jda.api.utils.data.DataArray;
import net.dv8tion.jda.api.utils.data.DataObject;
import net.dv8tion.jda.api.utils.data.DataType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

// 설치된 게임의 한국어 · 영어 텍스트에 림버스 컴퍼니 위키(wiki.gg)의 수치를 붙인 인격 목록
class IdentityCatalog {

    // 내성은 받는 피해 배율. 위키 표기를 읽지 못한 값은 null.
    record Stats(Integer rarity, Integer season, Double slash, Double pierce, Double blunt) {
    }

    record SkillStats(String sin, String type, Integer power, String coinPower, Integer coins) {
    }

    // stats 는 위키에서 짝을 찾지 못하면 null
    record Skill(int id, String name, String text, SkillStats stats) {
    }

    record Passive(int id, String name, String text, boolean support, String condition) {
    }

    record Identity(int id, String title, String sinner, Stats stats, List<Skill> skills, List<Passive> passives) {
    }

    private static final Map<String, String> SINS = Map.of(
            "wrath", "분노", "lust", "색욕", "sloth", "나태", "gluttony", "탐식", "gloom", "우울", "pride", "오만", "envy", "질투");

    private static final Map<String, String> TYPES = Map.of(
            "slash", "참격", "pierce", "관통", "blunt", "타격", "guard", "방어", "evade", "회피");

    // 위키 내성 표기는 Ineff · Ineff. · Ineffective 가 섞여 있어 접두어로 본다
    private static final Map<String, Double> RESISTS = Map.of(
            "fatal", 2.0, "weak", 1.5, "normal", 1.0, "endure", 0.75, "ineff", 0.5);

    private static final Pattern COMMENT = Pattern.compile("<!--.*?-->", Pattern.DOTALL);

    private static final Pattern PARAM_KEY = Pattern.compile("\\s*(\\w+)\\s*=");

    // 숫자 접두(3passive1)는 동기화 하위 단계 칸
    private static final Pattern PASSIVE_SLOT = Pattern.compile("passive\\d+");

    private static final Pattern REQUIREMENT = Pattern.compile("(\\d+)(?:\\s*(owned|res)\\.?)?", Pattern.CASE_INSENSITIVE);

    // 유니티 리치 텍스트 태그만 지운다. 본문에 <라만차랜드> 같은 글자 그대로의 꺾쇠가 있다.
    private static final Pattern MARKUP = Pattern.compile("</?(?:color|style|link|sprite|mark|noparse|b|u|s)(?:[= ][^>]*)?>");

    private static final Pattern KEYWORD = Pattern.compile("\\[(\\w+)]");

    private static final Logger log = LoggerFactory.getLogger(IdentityCatalog.class);

    // 게임 설치 폴더 기준
    private static final String LOCALIZE = "LimbusCompany_Data/Assets/Resources_moved/Localize";

    private static final Pattern LOCALIZE_FILE = Pattern.compile("(?:KR|EN)_(?:Personalities|Skills|Passive|Bufs|SkillTag).*\\.json");

    private static final URI WIKI_API = URI.create("https://limbuscompany.wiki.gg/api.php");

    // MediaWiki 는 문서 내용을 요청 하나에 50개까지 준다
    private static final int WIKI_BATCH = 50;

    // MediaWiki API 규약상 연락처가 있는 User-Agent
    private static final String USER_AGENT = "DiscordBotPractice (https://github.com/minky5004/DiscordBotPractice)";

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);

    // 문서 50개 내용이 한 응답이라 공지 API 보다 길게
    private static final Duration WIKI_TIMEOUT = Duration.ofSeconds(30);

    private static final Duration REFRESH_INTERVAL = Duration.ofHours(24);

    private static final Duration RETRY_INTERVAL = Duration.ofHours(1);

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();

    // JDA 가 내려간 뒤 JVM 이 이 스레드에 붙들리지 않게 데몬으로
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "limbus-identity");
        thread.setDaemon(true);
        return thread;
    });

    private final Path localize;

    // 조회는 JDA 스레드, 교체는 갱신 스레드. 목록을 통째로 바꿔 끼운다.
    private volatile List<Identity> identities = List.of();

    // 위키가 실패하면 마지막으로 받은 문서로 조립한다. 갱신 스레드에서만 만진다.
    private Map<String, String> pages = Map.of();

    private boolean wikiFailed;

    IdentityCatalog(Path gameDir) {
        this.localize = gameDir.resolve(LOCALIZE);
    }

    boolean hasGameFiles() {
        return Files.isDirectory(localize);
    }

    List<Identity> identities() {
        return identities;
    }

    void start() {
        executor.execute(this::refresh);
    }

    void refresh() {
        Duration next = REFRESH_INTERVAL;
        try {
            wikiFailed = false;
            List<Identity> built = build(readLocalize(localize), this::fetchWiki);
            identities = built;
            log.info("인격 목록 {}개 · 수치 있는 인격 {}개", built.size(), built.stream().filter(identity -> identity.stats() != null).count());
            if (wikiFailed) {
                next = RETRY_INTERVAL;
            }
        } catch (IOException | RuntimeException e) {
            // Steam 패치 도중 반쯤 쓰인 파일 등. 이전 목록을 그대로 쓴다.
            log.warn("인격 목록 갱신 실패", e);
            next = RETRY_INTERVAL;
        } finally {
            // 태스크 밖으로 예외가 새도 다음 갱신은 잡혀 있게
            executor.schedule(this::refresh, next.toMinutes(), TimeUnit.MINUTES);
        }
    }

    private Map<String, String> fetchWiki(List<String> titles) {
        Map<String, String> fetched = new HashMap<>();
        try {
            for (int i = 0; i < titles.size(); i += WIKI_BATCH) {
                List<String> batch = titles.subList(i, Math.min(i + WIKI_BATCH, titles.size()));
                fetched.putAll(wikiPages(postWiki(batch), batch));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            wikiFailed = true;
            return pages;
        } catch (IOException | RuntimeException e) {
            log.warn("위키 수치 받기 실패 · 이전 수치 사용", e);
            wikiFailed = true;
            return pages;
        }
        pages = fetched;
        return fetched;
    }

    // 제목 50개를 쿼리 문자열에 실으면 URL 이 길어져 POST 로
    private String postWiki(List<String> titles) throws IOException, InterruptedException {
        String form = "action=query&prop=revisions&rvprop=content&rvslots=main&redirects=1&format=json&formatversion=2&titles="
                + URLEncoder.encode(String.join("|", titles), StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder(WIKI_API)
                .timeout(WIKI_TIMEOUT)
                .header("User-Agent", USER_AGENT)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("위키 응답 " + response.statusCode());
        }
        return response.body();
    }

    // 응답의 문서 제목은 정규화 · 리다이렉트를 거친 뒤의 것이다. 요청한 제목으로 되돌려 담는다.
    static Map<String, String> wikiPages(String json, List<String> titles) {
        DataObject query = DataObject.fromJson(json).getObject("query");
        Map<String, String> normalized = renames(query, "normalized");
        Map<String, String> redirects = renames(query, "redirects");
        Map<String, String> contents = new HashMap<>();
        DataArray list = query.optArray("pages").orElseGet(DataArray::empty);
        for (int i = 0; i < list.length(); i++) {
            DataObject page = list.getObject(i);
            DataArray revisions = page.optArray("revisions").orElseGet(DataArray::empty);
            if (revisions.length() > 0) {
                contents.put(page.getString("title"), revisions.getObject(0).getObject("slots").getObject("main").getString("content"));
            }
        }

        Map<String, String> result = new HashMap<>();
        for (String title : titles) {
            String resolved = normalized.getOrDefault(title, title);
            String content = contents.get(redirects.getOrDefault(resolved, resolved));
            if (content != null) {
                result.put(title, content);
            }
        }
        return result;
    }

    private static Map<String, String> renames(DataObject query, String key) {
        Map<String, String> renames = new HashMap<>();
        DataArray list = query.optArray(key).orElseGet(DataArray::empty);
        for (int i = 0; i < list.length(); i++) {
            renames.put(list.getObject(i).getString("from"), list.getObject(i).getString("to"));
        }
        return renames;
    }

    static Map<String, byte[]> readLocalize(Path localize) throws IOException {
        Map<String, byte[]> files = new TreeMap<>();
        for (String language : List.of("kr", "en")) {
            try (Stream<Path> paths = Files.list(localize.resolve(language))) {
                for (Path path : (Iterable<Path>) paths::iterator) {
                    String name = path.getFileName().toString();
                    if (LOCALIZE_FILE.matcher(name).matches()) {
                        files.put(name, Files.readAllBytes(path));
                    }
                }
            }
        }
        return files;
    }

    static List<Identity> build(Map<String, byte[]> files, Function<List<String>, Map<String, String>> wiki) {
        Map<Integer, DataObject> identities = rows(files, "KR_Personalities");
        // 9999 는 지원 유닛, 40501 은 외형 투영. 인격만 남긴다.
        identities.keySet().removeIf(id -> id < 10000 || id >= 20000);
        Map<Integer, DataObject> enIdentities = rows(files, "EN_Personalities");
        Map<Integer, DataObject> skills = rows(files, "KR_Skills");
        Map<Integer, DataObject> enSkills = rows(files, "EN_Skills");
        Map<Integer, DataObject> passives = rows(files, "KR_Passive");
        Map<Integer, DataObject> enPassives = rows(files, "EN_Passive");
        Map<String, String> tags = names(files, "KR_Bufs");
        tags.putAll(names(files, "KR_SkillTag"));

        Map<Integer, String> titles = new TreeMap<>();
        enIdentities.forEach((id, en) -> {
            if (identities.containsKey(id)) {
                titles.put(id, wikiTitle(en.getString("title"), en.getString("name")));
            }
        });
        Map<String, String> pages = wiki.apply(List.copyOf(titles.values()));

        List<Identity> result = new ArrayList<>();
        identities.forEach((id, kr) -> {
            String page = pages.get(titles.get(id));
            Map<String, String> idPage = page == null ? null : templates(page, "IDPage").stream().findFirst().orElse(null);
            Map<String, Map<String, String>> wikiSkills = new LinkedHashMap<>();
            Map<String, Map<String, String>> wikiPassives = new LinkedHashMap<>();
            if (idPage != null) {
                for (Map<String, String> skill : templates(page, "UptieSkills")) {
                    wikiSkills.putIfAbsent(nameKey(skill.getOrDefault("name", "")), skill);
                }
                idPage.forEach((slot, value) -> {
                    if (PASSIVE_SLOT.matcher(slot).matches()) {
                        templates(value, "Passive").stream().findFirst()
                                .ifPresent(passive -> wikiPassives.put(nameKey(passive.getOrDefault("1", "")), passive));
                    }
                });
            }

            List<Skill> identitySkills = new ArrayList<>();
            skills.forEach((skillId, skill) -> {
                if (skillId / 100 != id) {
                    return;
                }
                DataObject top = lastLevel(skill);
                Map<String, String> stats = wikiSkills.get(nameKey(englishSkillName(enSkills.get(skillId))));
                identitySkills.add(new Skill(skillId, top.getString("name", ""), skillText(top, tags),
                        stats == null ? null : skillStats(stats)));
            });

            // 동기화 단계마다 ID 가 따로 있고(01 · 11) 이름이 같다. 같은 이름은 뒤 ID 하나만.
            Map<String, Passive> identityPassives = new LinkedHashMap<>();
            passives.forEach((passiveId, passive) -> {
                if (passiveId / 100 != id) {
                    return;
                }
                DataObject en = enPassives.get(passiveId);
                String key = en == null ? "#" + passiveId : nameKey(en.getString("name", ""));
                Map<String, String> stats = wikiPassives.get(key);
                // 끝번호 21 · 31 이 서포트 패시브 (전 인격 공통, 실측)
                boolean support = passiveId % 100 == 21 || passiveId % 100 == 31;
                identityPassives.put(key, new Passive(passiveId, passive.getString("name", ""),
                        richText(passive.getString("desc", ""), tags), support, stats == null ? null : condition(stats)));
            });

            result.add(new Identity(id, oneLine(kr.getString("title", "")), kr.getString("name", ""),
                    idPage == null ? null : stats(idPage), identitySkills, List.copyOf(identityPassives.values())));
        });
        return result;
    }

    static String wikiTitle(String title, String name) {
        return oneLine(title + " " + name).replaceAll("\\s*([【】])\\s*", "$1").replaceAll("\\s+", " ").strip();
    }

    // 인격 칭호는 게임 화면용 줄바꿈을 품고 있다
    private static String oneLine(String title) {
        return title.replace("::\n", "::").replace('\n', ' ');
    }

    // 이름이 name 인 틀마다 파라미터를 뽑는다. 이름 없는 인자는 MediaWiki 처럼 "1" · "2" 로.
    static List<Map<String, String>> templates(String wikitext, String name) {
        String text = COMMENT.matcher(wikitext).replaceAll("");
        String open = "{{" + name;
        List<Map<String, String>> found = new ArrayList<>();
        for (int start = text.indexOf(open); start >= 0; start = text.indexOf(open, start + open.length())) {
            int from = start + open.length();
            // {{Passive 로 {{PassiveIcon 을 잡지 않도록
            if (from < text.length() && !(text.charAt(from) == '|' || text.charAt(from) == '}' || Character.isWhitespace(text.charAt(from)))) {
                continue;
            }
            found.add(params(text, from));
        }
        return found;
    }

    // 중첩된 {{ }} · [[ ]] 안의 | 는 인자 구분이 아니다
    private static Map<String, String> params(String text, int from) {
        List<String> args = new ArrayList<>();
        StringBuilder arg = null;
        int depth = 0;
        for (int i = from; i < text.length(); i++) {
            boolean opens = text.startsWith("{{", i) || text.startsWith("[[", i);
            boolean closes = text.startsWith("}}", i) || text.startsWith("]]", i);
            if (closes && depth == 0) {
                break;
            }
            if (opens || closes) {
                depth += opens ? 1 : -1;
                if (arg != null) {
                    arg.append(text, i, i + 2);
                }
                i++;
                continue;
            }
            char c = text.charAt(i);
            if (c == '|' && depth == 0) {
                if (arg != null) {
                    args.add(arg.toString());
                }
                arg = new StringBuilder();
            } else if (arg != null) {
                arg.append(c);
            }
        }
        if (arg != null) {
            args.add(arg.toString());
        }

        Map<String, String> params = new LinkedHashMap<>();
        int position = 0;
        for (String value : args) {
            Matcher key = PARAM_KEY.matcher(value);
            if (key.lookingAt()) {
                params.put(key.group(1), value.substring(key.end()).strip());
            } else {
                params.put(String.valueOf(++position), value.strip());
            }
        }
        return params;
    }

    // 치환 뒤에 지운다. 사전 이름에도 마크업이 있다(<s>중지</s>).
    static String richText(String desc, Map<String, String> tags) {
        String text = KEYWORD.matcher(desc).replaceAll(match -> Matcher.quoteReplacement(tags.getOrDefault(match.group(1), match.group())));
        return MARKUP.matcher(text).replaceAll("");
    }

    private static Map<Integer, DataObject> rows(Map<String, byte[]> files, String prefix) {
        Map<Integer, DataObject> rows = new TreeMap<>();
        forEachRow(files, prefix, row -> {
            if (row.isType("id", DataType.INT)) {
                rows.put(row.getInt("id"), row);
            }
        });
        return rows;
    }

    private static Map<String, String> names(Map<String, byte[]> files, String prefix) {
        Map<String, String> names = new LinkedHashMap<>();
        forEachRow(files, prefix, row -> {
            if (row.isType("id", DataType.STRING) && row.hasKey("name")) {
                names.put(row.getString("id"), row.getString("name"));
            }
        });
        return names;
    }

    private static void forEachRow(Map<String, byte[]> files, String prefix, Consumer<DataObject> action) {
        files.forEach((name, data) -> {
            if (name.startsWith(prefix)) {
                // 바이트로 넘겨야 파서가 BOM 을 건너뛴다 (EN 파일 일부에 BOM)
                DataArray list = DataObject.fromJson(data).optArray("dataList").orElseGet(DataArray::empty);
                for (int i = 0; i < list.length(); i++) {
                    action.accept(list.getObject(i));
                }
            }
        });
    }

    private static DataObject lastLevel(DataObject skill) {
        DataArray levels = skill.getArray("levelList");
        return levels.getObject(levels.length() - 1);
    }

    private static String englishSkillName(DataObject en) {
        return en == null ? "" : lastLevel(en).getString("name", "");
    }

    // 위키 스킬명에는 한자 병기(Chainstrike [<b>連擊</b>])가 붙기도 한다
    private static String nameKey(String name) {
        return name.replaceAll("<[^>]*>", "").replaceAll("\\[[^]]*]", "").toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private static String skillText(DataObject level, Map<String, String> tags) {
        List<String> lines = new ArrayList<>();
        String desc = level.getString("desc", "");
        if (!desc.isBlank()) {
            lines.add(richText(desc, tags));
        }
        DataArray coins = level.optArray("coinlist").orElseGet(DataArray::empty);
        for (int i = 0; i < coins.length(); i++) {
            DataArray descs = coins.getObject(i).optArray("coindescs").orElseGet(DataArray::empty);
            List<String> effects = new ArrayList<>();
            for (int j = 0; j < descs.length(); j++) {
                String effect = richText(descs.getObject(j).getString("desc", ""), tags);
                if (!effect.isBlank()) {
                    effects.add(effect);
                }
            }
            if (!effects.isEmpty()) {
                lines.add("코인" + (i + 1) + " " + String.join(" · ", effects));
            }
        }
        return String.join("\n", lines);
    }

    // 숫자 접두 없는 값이 동기화 최고 단계
    private static Stats stats(Map<String, String> page) {
        return new Stats(integer(page.get("rarity")), integer(page.get("season")),
                resist(page.get("slash")), resist(page.get("pierce")), resist(page.get("blunt")));
    }

    private static SkillStats skillStats(Map<String, String> skill) {
        String coinPower = skill.get("cpower");
        return new SkillStats(SINS.get(lower(skill.get("sin"))), TYPES.get(lower(skill.get("type"))), integer(skill.get("spower")),
                coinPower == null || coinPower.isBlank() ? null : coinPower.replaceAll("\\s", ""), integer(skill.get("coin")));
    }

    // 발동 조건. 위키에는 "5 Owned" · "3 Res." · 종류 없이 "3" 이 섞여 있다.
    private static String condition(Map<String, String> passive) {
        List<String> parts = new ArrayList<>();
        for (String suffix : List.of("", "2", "3")) {
            String sin = SINS.get(lower(passive.get("sin" + suffix)));
            Matcher requirement = REQUIREMENT.matcher(passive.getOrDefault("req" + suffix, "").strip());
            if (sin == null || !requirement.matches()) {
                continue;
            }
            String kind = requirement.group(2) == null ? "" : requirement.group(2).equalsIgnoreCase("owned") ? " 보유" : " 공명";
            parts.add(sin + " " + requirement.group(1) + kind);
        }
        return parts.isEmpty() ? null : String.join(" · ", parts);
    }

    private static Double resist(String value) {
        String lower = lower(value);
        for (Map.Entry<String, Double> resist : RESISTS.entrySet()) {
            if (lower.startsWith(resist.getKey())) {
                return resist.getValue();
            }
        }
        try {
            return Double.valueOf(lower);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer integer(String value) {
        try {
            return value == null ? null : Integer.valueOf(value.strip());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // Map.of 는 null 키 조회에 NPE 를 던진다. 위키에 없는 파라미터는 빈 문자열로.
    private static String lower(String value) {
        return value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
    }
}
