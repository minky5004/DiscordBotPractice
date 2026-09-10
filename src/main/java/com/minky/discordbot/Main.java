package com.minky.discordbot;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

public class Main {

    private static final Path ENV_FILE = Path.of(".env");
    private static final String TOKEN_KEY = "DISCORD_BOT_TOKEN";
    private static final String DB_URL_KEY = "DB_URL";
    private static final String DB_USER_KEY = "DB_USER";
    private static final String DB_PASSWORD_KEY = "DB_PASSWORD";

    public static void main(String[] args) throws IOException, InterruptedException, SQLException {
        Properties env = readEnv(ENV_FILE);
        List<String> missing = List.of(TOKEN_KEY, DB_URL_KEY, DB_USER_KEY, DB_PASSWORD_KEY).stream()
                .filter(key -> env.getProperty(key, "").isBlank())
                .toList();
        if (!missing.isEmpty()) {
            System.err.println(".env 파일에서 " + String.join(", ", missing) + " 값을 찾지 못했습니다.");
            System.err.println(".env.example 을 .env 로 복사한 뒤 값을 채워 주세요.");
            return;
        }

        // DB 가 없으면 로그인 전에 실패한다
        ReminderStore store = ReminderStore.connect(
                env.getProperty(DB_URL_KEY), env.getProperty(DB_USER_KEY), env.getProperty(DB_PASSWORD_KEY));

        JDA jda = JDABuilder.createLight(env.getProperty(TOKEN_KEY), Collections.emptyList())
                .addEventListeners(new PingPongListener())
                .build()
                .awaitReady();

        // 채널 캐시는 awaitReady 뒤에야 채워지므로 복구도 그 뒤에
        EnkephalinListener enkephalin = new EnkephalinListener(jda, store);
        enkephalin.restore();
        jda.addEventListener(enkephalin);

        jda.updateCommands().addCommands(PingPongListener.COMMAND, EnkephalinListener.COMMAND, EnkephalinListener.CANCEL).complete();

        System.out.println("봇이 정상적으로 로그인되었습니다");
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
