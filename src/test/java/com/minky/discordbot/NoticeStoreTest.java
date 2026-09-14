package com.minky.discordbot;

import com.minky.discordbot.MaintenanceAlert.Maintenance;
import com.minky.discordbot.NoticeListener.Notice;
import com.minky.discordbot.NoticeStore.NoticeChannel;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
class NoticeStoreTest {

    @Container
    static final PostgreSQLContainer DB = new PostgreSQLContainer(DockerImageName.parse("postgres:17"));

    private static final long GUILD = 1L;
    private static final Notice A = new Notice("1", "a", 100L, "");
    private static final Notice B = new Notice("2", "b", 200L, "");
    private static final Notice C = new Notice("3", "c", 300L, "");
    private static final Instant T10 = Instant.parse("2026-09-17T01:00:00Z");
    private static final Instant T12 = Instant.parse("2026-09-17T03:00:00Z");

    private static NoticeStore store;

    @BeforeAll
    static void connect() {
        store = new NoticeStore(Main.connectDatabase(DB.getJdbcUrl(), DB.getUsername(), DB.getPassword()));
    }

    @BeforeEach
    void clear() throws SQLException {
        try (Connection connection = DB.createConnection(""); Statement statement = connection.createStatement()) {
            statement.execute("TRUNCATE posted_notice, notice_channel, maintenance");
        }
    }

    @Test
    void saveMaintenanceReplacesOnlyUnconfirmedWindow() throws SQLException {
        Maintenance usual = new Maintenance("g", T10, T12);
        Maintenance read = new Maintenance("g", T10.minusSeconds(4 * 3600), T12);
        assertEquals(Optional.empty(), store.findMaintenanceConfirmed("g"));

        store.saveMaintenance(usual, false);
        assertEquals(Optional.of(false), store.findMaintenanceConfirmed("g"));

        store.saveMaintenance(read, true);
        store.saveMaintenance(usual, false);

        assertEquals(Optional.of(true), store.findMaintenanceConfirmed("g"));
        assertEquals(List.of(read), store.findMaintenanceEndingAfter(T10));
    }

    @Test
    void findMaintenanceSkipsFinishedOnes() throws SQLException {
        store.saveMaintenance(new Maintenance("past", T10.minusSeconds(86400 * 7), T12.minusSeconds(86400 * 7)), true);
        store.saveMaintenance(new Maintenance("now", T10, T12), true);

        assertEquals(List.of(new Maintenance("now", T10, T12)), store.findMaintenanceEndingAfter(T12.minusSeconds(1)));
    }

    @Test
    void markNewSeedsSilentlyThenReportsOnlyUnseen() throws SQLException {
        assertEquals(List.of(), store.markNew(List.of(A, B)));

        assertEquals(List.of(C), store.markNew(List.of(A, B, C)));
        assertEquals(List.of(), store.markNew(List.of(A, B, C)));
    }

    @Test
    void setChannelReplacesSettingOfSameGuild() throws SQLException {
        store.setChannel(new NoticeChannel(GUILD, 10L, 100L));
        store.setChannel(new NoticeChannel(GUILD, 20L, null));

        assertEquals(List.of(new NoticeChannel(GUILD, 20L, null)), store.findChannels());
    }

    @Test
    void removeChannelReportsWhetherSettingExisted() throws SQLException {
        assertFalse(store.removeChannel(GUILD));

        store.setChannel(new NoticeChannel(GUILD, 10L, 100L));

        assertTrue(store.removeChannel(GUILD));
        assertEquals(List.of(), store.findChannels());
    }
}
