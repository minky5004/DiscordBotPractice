package com.minky.discordbot;

import com.minky.discordbot.MaintenanceAlert.Maintenance;
import com.minky.discordbot.NoticeListener.Notice;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

// 중계한 공지 기록 · 서버별 공지 채널 설정 · 정기 점검 시각
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

    boolean hasMaintenance(String gid) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT 1 FROM maintenance WHERE gid = ?")) {
            statement.setString(1, gid);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next();
            }
        }
    }

    // 먼저 읽은 시각을 지킨다. 같은 공지를 두 번 등록해도 알림 시각이 흔들리지 않도록.
    void saveMaintenance(Maintenance maintenance) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO maintenance (gid, starts_at, ends_at) VALUES (?, ?, ?) ON CONFLICT DO NOTHING")) {
            statement.setString(1, maintenance.gid());
            statement.setObject(2, maintenance.startsAt().atOffset(ZoneOffset.UTC));
            statement.setObject(3, maintenance.endsAt().atOffset(ZoneOffset.UTC));
            statement.executeUpdate();
        }
    }

    List<Maintenance> findMaintenanceEndingAfter(Instant instant) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT gid, starts_at, ends_at FROM maintenance WHERE ends_at > ? ORDER BY starts_at")) {
            statement.setObject(1, instant.atOffset(ZoneOffset.UTC));
            try (ResultSet rows = statement.executeQuery()) {
                List<Maintenance> maintenances = new ArrayList<>();
                while (rows.next()) {
                    maintenances.add(new Maintenance(rows.getString("gid"),
                            rows.getObject("starts_at", OffsetDateTime.class).toInstant(),
                            rows.getObject("ends_at", OffsetDateTime.class).toInstant()));
                }
                return maintenances;
            }
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
