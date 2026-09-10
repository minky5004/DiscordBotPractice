package com.minky.discordbot;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MainTest {

    private static final String TOKEN_KEY = "DISCORD_BOT_TOKEN";

    @TempDir
    Path dir;

    @Test
    void stripsTrailingWhitespaceFromToken() throws IOException {
        Path envFile = dir.resolve(".env");
        Files.writeString(envFile, "DISCORD_BOT_TOKEN=abc.def.ghi  \n");

        assertEquals("abc.def.ghi", Main.readEnv(envFile).getProperty(TOKEN_KEY));
    }

    @Test
    void returnsNullWhenFileMissing() throws IOException {
        assertNull(Main.readEnv(dir.resolve("nothing-here")).getProperty(TOKEN_KEY));
    }

    @Test
    void returnsNullWhenKeyMissing() throws IOException {
        Path envFile = dir.resolve(".env");
        Files.writeString(envFile, "OTHER_KEY=value\n");

        assertNull(Main.readEnv(envFile).getProperty(TOKEN_KEY));
    }
}
