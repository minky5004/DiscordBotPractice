package com.minky.discordbot;

import com.minky.discordbot.NoticeListener.Notice;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

// 중계한 공지 기록과 서버별 공지 채널 설정
class NoticeStore {

    record NoticeChannel(long guildId, long channelId, Long roleId) {
    }

    private final DataSource dataSource;

    NoticeStore(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    // 처음 보는 공지만 입력 순서대로 돌려준다. 기록이 비어 있던 첫 기동은 과거 공지가 한꺼번에 올라가지 않게 기록만 한다.
    // 한 트랜잭션이라 중간에 실패하면 아무것도 기록되지 않고 다음 주기에 같은 공지를 다시 본다.
    List<Notice> markNew(List<Notice> notices) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            boolean firstRun;
            try (PreparedStatement statement = connection.prepareStatement("SELECT EXISTS (SELECT 1 FROM posted_notice)");
                 ResultSet rows = statement.executeQuery()) {
                rows.next();
                firstRun = !rows.getBoolean(1);
            }
            List<Notice> fresh = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO posted_notice (gid) VALUES (?) ON CONFLICT DO NOTHING")) {
                for (Notice notice : notices) {
                    statement.setString(1, notice.gid());
                    if (statement.executeUpdate() > 0) {
                        fresh.add(notice);
                    }
                }
            }
            connection.commit();
            return firstRun ? List.of() : fresh;
        }
    }

    void setChannel(NoticeChannel channel) throws SQLException {
        String sql = """
                INSERT INTO notice_channel (guild_id, channel_id, role_id) VALUES (?, ?, ?)
                ON CONFLICT (guild_id) DO UPDATE SET channel_id = EXCLUDED.channel_id, role_id = EXCLUDED.role_id
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, channel.guildId());
            statement.setLong(2, channel.channelId());
            statement.setObject(3, channel.roleId(), Types.BIGINT);
            statement.executeUpdate();
        }
    }

    boolean removeChannel(long guildId) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM notice_channel WHERE guild_id = ?")) {
            statement.setLong(1, guildId);
            return statement.executeUpdate() > 0;
        }
    }

    List<NoticeChannel> findChannels() throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT guild_id, channel_id, role_id FROM notice_channel");
             ResultSet rows = statement.executeQuery()) {
            List<NoticeChannel> channels = new ArrayList<>();
            while (rows.next()) {
                channels.add(new NoticeChannel(rows.getLong("guild_id"), rows.getLong("channel_id"),
                        rows.getObject("role_id", Long.class)));
            }
            return channels;
        }
    }
}
