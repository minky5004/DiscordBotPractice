package com.mario.rl.discordbot;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MainTest {

    @TempDir
    Path dir;

    @Test
    void stripsTrailingWhitespaceFromToken() throws IOException {
        Path envFile = dir.resolve(".env");
        Files.writeString(envFile, "DISCORD_BOT_TOKEN=abc.def.ghi  \n");

        assertEquals("abc.def.ghi", Main.readToken(envFile));
    }

    @Test
    void returnsNullWhenFileMissing() throws IOException {
        assertNull(Main.readToken(dir.resolve("nothing-here")));
    }

    @Test
    void returnsNullWhenKeyMissing() throws IOException {
        Path envFile = dir.resolve(".env");
        Files.writeString(envFile, "OTHER_KEY=value\n");

        assertNull(Main.readToken(envFile));
    }
}
