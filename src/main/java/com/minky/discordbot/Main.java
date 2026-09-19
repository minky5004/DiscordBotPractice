package com.minky.discordbot;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.flywaydb.core.Flyway;
import org.postgresql.ds.PGSimpleDataSource;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Stream;

public class Main {

    private static final Path ENV_FILE = Path.of(".env");
    private static final String TOKEN_KEY = "DISCORD_BOT_TOKEN";
    private static final String DB_URL_KEY = "DB_URL";
    private static final String DB_USER_KEY = "DB_USER";
    private static final String DB_PASSWORD_KEY = "DB_PASSWORD";

    // 게임 설치 폴더. 없으면 게임 데이터 명령(인격 · 키워드 · E.G.O · 기프트) 없이 뜬다.
    private static final String LIMBUS_DIR_KEY = "LIMBUS_DIR";

    private static final String IDENTITY_COMMANDS =
            "/" + IdentityListener.COMMAND.getName() + " · /" + IdentitySearchListener.COMMAND.getName()
                    + " · /" + KeywordListener.COMMAND.getName() + " · /" + EgoListener.COMMAND.getName()
                    + " · /" + GiftListener.COMMAND.getName() + " · /" + GiftSearchListener.COMMAND.getName();

    private static final List<String> REQUIRED_KEYS = List.of(TOKEN_KEY, DB_URL_KEY, DB_USER_KEY, DB_PASSWORD_KEY);

    private static final List<String> KEYS =
            Stream.concat(REQUIRED_KEYS.stream(), Stream.of(LIMBUS_DIR_KEY)).toList();

    public static void main(String[] args) throws IOException, InterruptedException, SQLException {
        Properties env = readConfig(ENV_FILE, System.getenv());
        List<String> missing = REQUIRED_KEYS.stream()
                .filter(key -> env.getProperty(key, "").isBlank())
                .toList();
        if (!missing.isEmpty()) {
            System.err.println("환경 변수 · .env 파일에서 " + String.join(", ", missing) + " 값을 찾지 못했습니다.");
            System.err.println(".env.example 을 .env 로 복사한 뒤 값을 채워 주세요.");
            return;
        }

        // DB 가 없으면 로그인 전에 실패한다
        DataSource database = connectDatabase(
                env.getProperty(DB_URL_KEY), env.getProperty(DB_USER_KEY), env.getProperty(DB_PASSWORD_KEY));
        ReminderStore store = new ReminderStore(database);

        JDA jda = JDABuilder.createLight(env.getProperty(TOKEN_KEY), Collections.emptyList())
                .addEventListeners(new PingPongListener())
                .build()
                .awaitReady();

        // 채널 캐시는 awaitReady 뒤에야 채워지므로 복구도 그 뒤에
        EnkephalinListener enkephalin = new EnkephalinListener(jda, store);
        enkephalin.restore();
        NoticeStore noticeStore = new NoticeStore(database);
        MaintenanceAlert maintenance = new MaintenanceAlert(jda, noticeStore);
        // 폴러가 같은 점검을 다시 등록하지 않도록 복구가 먼저
        maintenance.restore();
        NoticeListener notice = new NoticeListener(jda, noticeStore, maintenance);
        notice.start();
        jda.addEventListener(enkephalin, notice);

        List<SlashCommandData> commands = new ArrayList<>(List.of(PingPongListener.COMMAND, EnkephalinListener.COMMAND,
                EnkephalinListener.CANCEL, NoticeListener.COMMAND, NoticeListener.CANCEL));
        IdentityCatalog identities = identityCatalog(env.getProperty(LIMBUS_DIR_KEY, ""));
        if (identities != null) {
            identities.start();
            jda.addEventListener(new IdentityListener(identities), new IdentitySearchListener(identities),
                    new KeywordListener(identities), new EgoListener(identities), new GiftListener(identities),
                    new GiftSearchListener(identities));
            commands.add(IdentityListener.COMMAND);
            commands.add(IdentitySearchListener.COMMAND);
            commands.add(KeywordListener.COMMAND);
            commands.add(EgoListener.COMMAND);
            commands.add(GiftListener.COMMAND);
            commands.add(GiftSearchListener.COMMAND);
        }
        jda.updateCommands().addCommands(commands).complete();

        System.out.println("봇이 정상적으로 로그인되었습니다");
    }

    // 게임 텍스트가 없으면 게임 데이터 명령은 등록하지 않는다. 나머지 명령은 그대로 뜬다.
    private static IdentityCatalog identityCatalog(String gameDir) {
        if (gameDir.isBlank()) {
            System.err.println(LIMBUS_DIR_KEY + " 값이 없어 " + IDENTITY_COMMANDS + " 을 등록하지 않습니다.");
            return null;
        }
        IdentityCatalog catalog;
        try {
            catalog = new IdentityCatalog(Path.of(gameDir));
        } catch (InvalidPathException e) {
            // 따옴표를 두른 경로 등. 로그인 뒤 자리라 여기서 터지면 명령 등록 없이 봇만 떠 있는다.
            System.err.println(LIMBUS_DIR_KEY + " 경로를 읽지 못해 " + IDENTITY_COMMANDS
                    + " 을 등록하지 않습니다 · " + gameDir + " · " + e.getMessage());
            return null;
        }
        if (!catalog.hasGameFiles()) {
            System.err.println(LIMBUS_DIR_KEY + " 경로에서 게임 텍스트를 찾지 못해 " + IDENTITY_COMMANDS
                    + " 을 등록하지 않습니다 · " + gameDir);
            return null;
        }
        return catalog;
    }

    // 스키마를 최신으로 올린 뒤에만 저장소에 넘긴다
    static DataSource connectDatabase(String url, String user, String password) {
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setURL(url);
        dataSource.setUser(user);
        dataSource.setPassword(password);
        // 슬래시 응답 기한이 3초라 기본 10초를 기다리면 DB 장애가 "상호작용 실패" 로 착지한다
        dataSource.setConnectTimeout(2);
        Flyway.configure().dataSource(dataSource).load().migrate();
        return dataSource;
    }

    // 컨테이너는 .env 없이 compose 가 넘긴 환경 변수로 뜬다 — DB_URL 호스트가 localhost 가 아니라 db
    static Properties readConfig(Path envFile, Map<String, String> environment) throws IOException {
        Properties config = readEnv(envFile);
        for (String key : KEYS) {
            String value = environment.get(key);
            if (value != null && !value.isBlank()) {
                config.setProperty(key, value.strip());
            }
        }
        return config;
    }

    static Properties readEnv(Path envFile) throws IOException {
        Properties env = new Properties();
        if (!Files.exists(envFile)) {
            return env;
        }
        try (Reader reader = Files.newBufferedReader(envFile)) {
            env.load(reader);
        }
        // 포털 복사에 딸려오는 후행 공백이 isBlank() 를 통과해 InvalidTokenException 으로 착지하던 경로
        env.replaceAll((key, value) -> ((String) value).strip());
        return env;
    }
}
