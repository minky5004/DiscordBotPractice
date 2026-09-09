package com.mario.rl.discordbot;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReminderSchedulerTest {

    private static final long USER = 1L;

    @Test
    void replacesEarlierReservationOfSameUser() throws InterruptedException {
        ReminderScheduler scheduler = new ReminderScheduler();
        AtomicInteger replaced = new AtomicInteger();
        CountDownLatch kept = new CountDownLatch(1);

        scheduler.schedule(USER, Duration.ofMillis(100), replaced::incrementAndGet);
        scheduler.schedule(USER, Duration.ofMillis(100), kept::countDown);

        assertTrue(kept.await(2, TimeUnit.SECONDS));
        assertEquals(0, replaced.get());
    }

    @Test
    void cancelStopsPendingReservation() throws InterruptedException {
        ReminderScheduler scheduler = new ReminderScheduler();
        AtomicInteger fired = new AtomicInteger();

        scheduler.schedule(USER, Duration.ofMillis(100), fired::incrementAndGet);

        assertTrue(scheduler.cancel(USER));
        Thread.sleep(300);
        assertEquals(0, fired.get());
    }

    @Test
    void cancelReportsAbsenceOfReservation() {
        assertFalse(new ReminderScheduler().cancel(USER));
    }

    @Test
    void forgetsFiredReservation() throws InterruptedException {
        ReminderScheduler scheduler = new ReminderScheduler();
        CountDownLatch fired = new CountDownLatch(1);

        scheduler.schedule(USER, Duration.ofMillis(50), fired::countDown);

        assertTrue(fired.await(2, TimeUnit.SECONDS));
        assertFalse(scheduler.cancel(USER));
    }
}
