package com.minky.discordbot;

import com.minky.discordbot.ReminderScheduler.Reminder;
import org.flywaydb.core.Flyway;
import org.postgresql.ds.PGSimpleDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

// 예약의 원본. ReminderScheduler 의 큐는 이 테이블에서 언제든 다시 만들 수 있는 사본이다.
class ReminderStore {

    private final DataSource dataSource;

    private ReminderStore(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    // 스키마를 최신으로 올린 뒤에만 저장소를 내준다
    static ReminderStore connect(String url, String user, String password) {
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setURL(url);
        dataSource.setUser(user);
        dataSource.setPassword(password);
        // 슬래시 응답 기한이 3초라 기본 10초를 기다리면 DB 장애가 "상호작용 실패" 로 착지한다
        dataSource.setConnectTimeout(2);
        Flyway.configure().dataSource(dataSource).load().migrate();
        return new ReminderStore(dataSource);
    }

    void save(Reminder reminder) throws SQLException {
        String sql = """
                INSERT INTO reminder (user_id, channel_id, fire_at) VALUES (?, ?, ?)
                ON CONFLICT (user_id) DO UPDATE SET channel_id = EXCLUDED.channel_id, fire_at = EXCLUDED.fire_at
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, reminder.userId());
            statement.setLong(2, reminder.channelId());
            statement.setObject(3, timestamp(reminder.fireAt()));
            statement.executeUpdate();
        }
    }

    boolean delete(long userId) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM reminder WHERE user_id = ?")) {
            statement.setLong(1, userId);
            return statement.executeUpdate() > 0;
        }
    }

    // 발화 도중 같은 유저가 새로 예약했으면 그 행은 시각이 달라 남는다
    void deleteFired(Reminder reminder) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM reminder WHERE user_id = ? AND fire_at = ?")) {
            statement.setLong(1, reminder.userId());
            statement.setObject(2, timestamp(reminder.fireAt()));
            statement.executeUpdate();
        }
    }

    List<Reminder> findAll() throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT user_id, channel_id, fire_at FROM reminder");
             ResultSet rows = statement.executeQuery()) {
            List<Reminder> reminders = new ArrayList<>();
            while (rows.next()) {
                reminders.add(new Reminder(rows.getLong("user_id"), rows.getLong("channel_id"),
                        rows.getObject("fire_at", OffsetDateTime.class).toInstant()));
            }
            return reminders;
        }
    }

    // TIMESTAMPTZ 는 마이크로초로 반올림해 저장한다. 나노초를 그대로 넘기면 deleteFired 의 등치 비교가 빗나가
    // 발화한 예약이 남고, 다음 기동에서 멘션이 한 번 더 간다.
    private static OffsetDateTime timestamp(Instant instant) {
        return instant.truncatedTo(ChronoUnit.MICROS).atOffset(ZoneOffset.UTC);
    }
}
