package com.mario.rl.discordbot;

import com.mario.rl.discordbot.ReminderScheduler.Reminder;
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
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
class ReminderStoreTest {

    @Container
    static final PostgreSQLContainer DB = new PostgreSQLContainer(DockerImageName.parse("postgres:17"));

    private static final long USER = 1L;
    private static final Instant T1 = Instant.ofEpochSecond(1_800_000_000L);
    private static final Instant T2 = Instant.ofEpochSecond(1_800_000_600L);

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

    @Test
    void saveReplacesReservationOfSameUser() throws SQLException {
        store.save(new Reminder(USER, 10L, T1));
        store.save(new Reminder(USER, 20L, T2));

        assertEquals(List.of(new Reminder(USER, 20L, T2)), store.findAll());
    }

    @Test
    void deleteFiredKeepsNewerReservation() throws SQLException {
        Reminder fired = new Reminder(USER, 10L, T1);
        Reminder newer = new Reminder(USER, 10L, T2);
        store.save(fired);
        store.save(newer);

        store.deleteFired(fired);

        assertEquals(List.of(newer), store.findAll());
    }

    @Test
    void deleteReportsWhetherReservationExisted() throws SQLException {
        assertFalse(store.delete(USER));

        store.save(new Reminder(USER, 10L, T1));

        assertTrue(store.delete(USER));
        assertEquals(List.of(), store.findAll());
    }
}
