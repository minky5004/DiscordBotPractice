package com.minky.discordbot;

import com.minky.discordbot.ReminderScheduler.Reminder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
class ReminderSchedulerTest {

    @Container
    static final PostgreSQLContainer DB = new PostgreSQLContainer(DockerImageName.parse("postgres:17"));

    private static final long USER = 1L;

    private static ReminderStore store;

    @BeforeAll
    static void connect() {
        store = ReminderStore.connect(DB.getJdbcUrl(), DB.getUsername(), DB.getPassword());
    }

    @BeforeEach
    void clear() throws SQLException {
        try (Connection connection = DB.createConnection(""); Statement statement = connection.createStatement()) {
            statement.execute("TRUNCATE reminder");
        }
    }

    private static Reminder after(Duration delay, long channelId) {
        return new Reminder(USER, channelId, Instant.now().plus(delay));
    }

    @Test
    void replacesEarlierReservationOfSameUser() throws Exception {
        Queue<Long> fired = new ConcurrentLinkedQueue<>();
        CountDownLatch kept = new CountDownLatch(1);
        ReminderScheduler scheduler = new ReminderScheduler(store, reminder -> {
            fired.add(reminder.channelId());
            kept.countDown();
        });

        scheduler.schedule(after(Duration.ofMillis(100), 10L));
        scheduler.schedule(after(Duration.ofMillis(100), 20L));

        assertTrue(kept.await(2, TimeUnit.SECONDS));
        Thread.sleep(200);
        assertEquals(List.of(20L), List.copyOf(fired));
    }

    @Test
    void cancelStopsPendingReservation() throws Exception {
        CountDownLatch fired = new CountDownLatch(1);
        ReminderScheduler scheduler = new ReminderScheduler(store, reminder -> fired.countDown());

        scheduler.schedule(after(Duration.ofMillis(100), 10L));

        assertTrue(scheduler.cancel(USER));
        assertFalse(fired.await(300, TimeUnit.MILLISECONDS));
        assertEquals(List.of(), store.findAll());
    }

    @Test
    void cancelReportsAbsenceOfReservation() throws SQLException {
        assertFalse(new ReminderScheduler(store, reminder -> {}).cancel(USER));
    }

    @Test
    void forgetsFiredReservation() throws Exception {
        CountDownLatch fired = new CountDownLatch(1);
        ReminderScheduler scheduler = new ReminderScheduler(store, reminder -> fired.countDown());

        scheduler.schedule(after(Duration.ofMillis(50), 10L));

        assertTrue(fired.await(2, TimeUnit.SECONDS));
        assertFalse(scheduler.cancel(USER));
        assertEquals(List.of(), store.findAll());
    }

    @Test
    void restoreFiresReservationSavedBeforeRestart() throws Exception {
        store.save(after(Duration.ofMillis(100), 10L));
        CountDownLatch fired = new CountDownLatch(1);

        new ReminderScheduler(store, reminder -> fired.countDown()).restore();

        assertTrue(fired.await(2, TimeUnit.SECONDS));
    }

    @Test
    void restoreFiresOverdueReservationImmediately() throws Exception {
        store.save(after(Duration.ofHours(-1), 10L));
        CountDownLatch fired = new CountDownLatch(1);

        new ReminderScheduler(store, reminder -> fired.countDown()).restore();

        assertTrue(fired.await(1, TimeUnit.SECONDS));
        assertEquals(List.of(), store.findAll());
    }
}
