package com.mario.rl.discordbot;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PingPongListenerTest {

    @Test
    void acceptsSurroundingWhitespace() {
        assertTrue(PingPongListener.isPingCommand("!ping"));
        assertTrue(PingPongListener.isPingCommand("!ping "));
        assertTrue(PingPongListener.isPingCommand("  !ping\n"));
    }

    @Test
    void rejectsOtherContent() {
        assertFalse(PingPongListener.isPingCommand("!pingpong"));
        assertFalse(PingPongListener.isPingCommand("ping"));
        assertFalse(PingPongListener.isPingCommand(""));
    }
}
