package com.mario.rl.discordbot;

import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Properties;

public class Main {

    private static final Path ENV_FILE = Path.of(".env");
    private static final String TOKEN_KEY = "DISCORD_BOT_TOKEN";

    public static void main(String[] args) throws IOException, InterruptedException {
        String token = readToken(ENV_FILE);
        if (token == null || token.isBlank()) {
            System.err.println(".env 파일에서 " + TOKEN_KEY + " 값을 찾지 못했습니다.");
            System.err.println(".env.example 을 .env 로 복사한 뒤 봇 토큰을 채워 주세요.");
            return;
        }

        JDABuilder.createLight(token, EnumSet.of(GatewayIntent.GUILD_MESSAGES, GatewayIntent.MESSAGE_CONTENT))
                .addEventListeners(new PingPongListener())
                .build()
                .awaitReady();

        System.out.println("봇이 정상적으로 로그인되었습니다");
    }

    static String readToken(Path envFile) throws IOException {
        if (!Files.exists(envFile)) {
            return null;
        }
        Properties env = new Properties();
        try (Reader reader = Files.newBufferedReader(envFile)) {
            env.load(reader);
        }
        String token = env.getProperty(TOKEN_KEY);
        return token == null ? null : token.strip();
    }
}
