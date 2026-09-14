package com.minky.discordbot;

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
import java.util.List;

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

    private static NoticeStore store;

    @BeforeAll
    static void connect() {
        store = new NoticeStore(Main.connectDatabase(DB.getJdbcUrl(), DB.getUsername(), DB.getPassword()));
    }

    @BeforeEach
    void clear() throws SQLException {
        try (Connection connection = DB.createConnection(""); Statement statement = connection.createStatement()) {
            statement.execute("TRUNCATE posted_notice, notice_channel");
        }
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
