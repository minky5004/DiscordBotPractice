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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
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

    // icon 은 위키 스킬 아이콘 URL · 위키에서 짝을 찾지 못하면 null
    record SkillStats(String sin, String type, Integer power, String coinPower, Integer coins, String icon) {
    }

    // stats 는 위키에서 짝을 찾지 못하면 null
    record Skill(int id, String name, String text, SkillStats stats) {
    }

    record Passive(int id, String name, String text, boolean support, String condition) {
    }

    // image 는 위키 전신 일러스트 URL · 규칙에 맞는 파일이 없으면 null
    record Identity(int id, String title, String sinner, String image, Stats stats, List<Skill> skills,
                    List<Passive> passives) {
    }

    // users 는 이 키워드를 쓰는 인격 · E.G.O 이름 · 인격이 먼저, 각자 게임 ID 순
    record Keyword(String id, String name, String desc, List<String> users) {
    }

    static final Map<String, String> SINS = Map.of(
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

    private static final Pattern LOCALIZE_FILE = Pattern.compile("(?:KR|EN)_(?:Personalities|Skills|Passive|Bufs|SkillTag|BattleKeywords|Egos).*\\.json");

    // 전투 공통 사전 · 이벤트 · 거울 던전 변형 파일은 같은 ID 에 다른 설명을 두기도 해 공통 쪽이 우선
    private static final String KEYWORD_FILE = "KR_BattleKeywords.json";

    private static final String KEYWORD_PREFIX = "KR_BattleKeywords";

    private static final URI WIKI_API = URI.create("https://limbuscompany.wiki.gg/api.php");

    // 파일 이름만으로 실제 이미지를 되돌려주는 미디어위키 경로
    private static final String FILE_PATH = "https://limbuscompany.wiki.gg/wiki/Special:FilePath/";

    private static final Pattern SPACES = Pattern.compile("\\s+");

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

    private volatile List<Keyword> keywords = List.of();

    private volatile List<EgoCatalog.Ego> egos = List.of();

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

    List<Keyword> keywords() {
        return keywords;
    }

    List<EgoCatalog.Ego> egos() {
        return egos;
    }

    void start() {
        executor.execute(this::refresh);
    }

    void refresh() {
        Duration next = REFRESH_INTERVAL;
        try {
            wikiFailed = false;
            Map<String, byte[]> files = readLocalize(localize);
            List<Identity> built = build(files, this::fetchWiki);
            if (built.isEmpty()) {
                // 게임 패치가 파일 이름을 바꾸면 예외 없이 0건이 된다. 돌아가던 목록을 비우지 않는다.
                log.warn("게임 폴더에서 인격을 하나도 읽지 못함 · 이전 목록 유지 · {}", localize);
                next = RETRY_INTERVAL;
                return;
            }
            identities = built;
            List<Keyword> read = keywords(files);
            if (read.isEmpty()) {
                // 인격과 같은 이유 · 키워드 파일만 이름이 바뀌어도 예외 없이 0건
                log.warn("게임 폴더에서 키워드를 하나도 읽지 못함 · 이전 목록 유지 · {}", localize);
                next = RETRY_INTERVAL;
            } else {
                keywords = read;
            }
            List<EgoCatalog.Ego> builtEgos = EgoCatalog.build(files, this::fetchWiki);
            if (builtEgos.isEmpty()) {
                log.warn("게임 폴더에서 E.G.O 를 하나도 읽지 못함 · 이전 목록 유지 · {}", localize);
                next = RETRY_INTERVAL;
            } else {
                egos = builtEgos;
            }
            log.info("인격 목록 {}개 · 수치 있는 인격 {}개 · 키워드 {}개 · E.G.O {}개 · 수치 있는 E.G.O {}개", built.size(),
                    built.stream().filter(identity -> identity.stats() != null).count(), keywords.size(),
                    egos.size(), egos.stream().filter(ego -> ego.risk() != null).count());
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
        // 인격과 E.G.O 가 따로 받는다 · 통째로 바꾸면 한쪽 실패 때 다른 쪽 문서로 되돌아간다
        Map<String, String> merged = new HashMap<>(pages);
        merged.putAll(fetched);
        pages = merged;
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
        identities.keySet().removeIf(id -> !isIdentity(id));
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
            // EN 텍스트가 아직 없는 인격은 위키 제목을 만들 수 없다. Map.of() 는 null 조회에 NPE.
            String page = pages.get(titles.getOrDefault(id, ""));
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
                if (top == null) {
                    return;
                }
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
                    page == null ? null : image(titles.get(id), page),
                    idPage == null ? null : stats(idPage), identitySkills, List.copyOf(identityPassives.values())));
        });
        return result;
    }

    // 9999 는 지원 유닛, 40501 은 외형 투영. 스킬 · 패시브 ID 는 인격 ID × 100 + n.
    static boolean isIdentity(int id) {
        return id >= 10000 && id < 20000;
    }

    // 인격 · E.G.O 의 스킬 · 패시브 본문에 [ID] 로 나오는 키워드만 · 옛 메커니즘(Burn)이 지금 것(Combustion)과 이름이 겹치고
    // 이벤트 파일엔 그 전투에만 쓰는 키워드가 섞여 있다.
    static List<Keyword> keywords(Map<String, byte[]> files) {
        Map<Integer, DataObject> identities = rows(files, "KR_Personalities");
        Map<Integer, String> sinners = new HashMap<>();
        identities.forEach((id, row) -> sinners.putIfAbsent(id / 100 % 100, row.getString("name", "")));
        Map<Integer, DataObject> egos = rows(files, "KR_Egos");
        Map<Integer, String> owners = new TreeMap<>();
        identities.forEach((id, row) -> {
            if (isIdentity(id)) {
                owners.put(id, "[" + oneLine(row.getString("title", "")) + "] " + row.getString("name", ""));
            }
        });
        egos.forEach((id, row) -> {
            if (EgoCatalog.isEgo(id)) {
                owners.put(id, "E.G.O " + row.getString("name", "") + " · " + sinners.getOrDefault(id / 100 % 100, "?"));
            }
        });

        // 키워드 ID → 쓰는 인격 · E.G.O ID. KR_Skills · KR_Passive 접두어가 E.G.O 파일(_Ego)까지 잡는다.
        Map<String, Set<Integer>> used = new HashMap<>();
        for (String prefix : List.of("KR_Skills", "KR_Passive")) {
            rows(files, prefix).forEach((id, row) -> {
                if (owners.containsKey(id / 100)) {
                    KEYWORD.matcher(row.toString()).results()
                            .forEach(match -> used.computeIfAbsent(match.group(1), key -> new TreeSet<>()).add(id / 100));
                }
            });
        }

        Map<String, String> tags = names(files, "KR_Bufs");
        Map<String, Keyword> byId = new LinkedHashMap<>();
        Consumer<DataObject> add = row -> {
            String id = row.getString("id", "");
            String desc = richText(row.getString("desc", ""), tags).strip();
            if (used.containsKey(id) && !desc.isEmpty()) {
                byId.putIfAbsent(id, new Keyword(id, MARKUP.matcher(row.getString("name", "")).replaceAll(""), desc, List.of()));
            }
        };
        // 공통 사전이 먼저 · 인격 전용 키워드(산나비 등)는 그 인격이 나온 이벤트 파일에만 있다
        forEachRow(files, KEYWORD_FILE, add);
        forEachRow(files, KEYWORD_PREFIX, add);

        // 이름과 설명이 같은 항목은 인격마다 ID 만 따로 둔 것이라 하나로 · 사용처는 합친다
        Map<List<String>, Keyword> first = new LinkedHashMap<>();
        Map<List<String>, Set<Integer>> users = new HashMap<>();
        byId.values().forEach(keyword -> {
            List<String> key = List.of(keyword.name(), keyword.desc());
            first.putIfAbsent(key, keyword);
            users.computeIfAbsent(key, k -> new TreeSet<>()).addAll(used.get(keyword.id()));
        });
        return first.entrySet().stream()
                .map(entry -> new Keyword(entry.getValue().id(), entry.getValue().name(), entry.getValue().desc(),
                        users.get(entry.getKey()).stream().map(owners::get).toList()))
                .toList();
    }


    // 이미지 파일명은 문서 제목에서 콜론이 빠진 꼴 · 인격 199건 중 197건이 둘 중 하나로 걸린다.
    // 전신 일러스트가 없는 인격은 대기 스프라이트로 · 이름을 위키텍스트에서 확인하므로 추가 조회가 없다
    static String image(String wikiTitle, String page) {
        if (wikiTitle == null) {
            return null;
        }
        String base = SPACES.matcher(wikiTitle.replace(":", " ")).replaceAll(" ").strip();
        return Stream.of(" Full.png", " Idle Sprite.png")
                .map(suffix -> base + suffix)
                .filter(page::contains)
                .findFirst()
                .map(file -> fileUrl(file, ""))
                .orElse(null);
    }

    // 파일 이름만으로 실제 이미지를 가리키는 주소
    static String fileUrl(String name, String suffix) {
        if (name == null || name.isBlank()) {
            return null;
        }
        return FILE_PATH + URLEncoder.encode(name.strip() + suffix, StandardCharsets.UTF_8).replace("+", "%20");
    }

    static String wikiTitle(String title, String name) {
        return oneLine(title + " " + name).replaceAll("\\s*([【】])\\s*", "$1").replaceAll("\\s+", " ").strip();
    }

    // 인격 칭호는 게임 화면용 줄바꿈을 품고 있다
    static String oneLine(String title) {
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

    static Map<Integer, DataObject> rows(Map<String, byte[]> files, String prefix) {
        Map<Integer, DataObject> rows = new TreeMap<>();
        forEachRow(files, prefix, row -> {
            if (row.isType("id", DataType.INT)) {
                rows.put(row.getInt("id"), row);
            }
        });
        return rows;
    }

    static Map<String, String> names(Map<String, byte[]> files, String prefix) {
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

    // 동기화 최고 단계. levelList 가 없거나 빈 행은 그 스킬만 버린다 — 하나 때문에 갱신 전체를 멈추지 않는다.
    static DataObject lastLevel(DataObject skill) {
        DataArray levels = skill.optArray("levelList").orElseGet(DataArray::empty);
        return levels.isEmpty() ? null : levels.getObject(levels.length() - 1);
    }

    static String englishSkillName(DataObject en) {
        DataObject top = en == null ? null : lastLevel(en);
        return top == null ? "" : top.getString("name", "");
    }

    // 위키 스킬명에는 한자 병기(Chainstrike [<b>連擊</b>])가 붙기도 한다
    static String nameKey(String name) {
        return name.replaceAll("<[^>]*>", "").replaceAll("\\[[^]]*]", "").toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    static String skillText(DataObject level, Map<String, String> tags) {
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

    static SkillStats skillStats(Map<String, String> skill) {
        String coinPower = skill.get("cpower");
        return new SkillStats(SINS.get(lower(skill.get("sin"))), TYPES.get(lower(skill.get("type"))), integer(skill.get("spower")),
                coinPower == null || coinPower.isBlank() ? null : coinPower.replaceAll("\\s", ""), integer(skill.get("coin")),
                // 아이콘 파라미터는 확장자 없는 파일 이름이다
                fileUrl(skill.get("icon"), ".png"));
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

    static Double resist(String value) {
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

    static Integer integer(String value) {
        try {
            return value == null ? null : Integer.valueOf(value.strip());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // Map.of 는 null 키 조회에 NPE 를 던진다. 위키에 없는 파라미터는 빈 문자열로.
    static String lower(String value) {
        return value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
    }
}
