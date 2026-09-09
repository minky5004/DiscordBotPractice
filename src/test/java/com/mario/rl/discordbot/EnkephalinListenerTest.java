package com.mario.rl.discordbot;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EnkephalinListenerTest {

    @Test
    void sixMinutesPerEnkephalin() {
        assertEquals(Duration.ofMinutes(60), EnkephalinListener.untilFull(132, 142));
    }

    @Test
    void zeroWhenAlreadyFull() {
        assertEquals(Duration.ZERO, EnkephalinListener.untilFull(142, 142));
    }

    @Test
    void zeroWhenOverCap() {
        assertEquals(Duration.ZERO, EnkephalinListener.untilFull(200, 142));
    }
}
