package com.mario.rl.discordbot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

// 유저당 예약 하나. 원본은 ReminderStore 이고, 여기 큐는 기동마다 restore() 로 다시 채운다.
class ReminderScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReminderScheduler.class);

    record Reminder(long userId, long channelId, Instant fireAt) {
    }

    private final ScheduledExecutorService executor = newExecutor();

    private final Map<Long, ScheduledFuture<?>> pending = new ConcurrentHashMap<>();

    private final ReminderStore store;

    private final Consumer<Reminder> onFire;

    ReminderScheduler(ReminderStore store, Consumer<Reminder> onFire) {
        this.store = store;
        this.onFire = onFire;
    }

    private static ScheduledExecutorService newExecutor() {
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1, task -> {
            // JDA 가 내려간 뒤 JVM 이 이 스레드에 붙들리지 않게 데몬으로
            Thread thread = new Thread(task, "enkephalin-reminder");
            thread.setDaemon(true);
            return thread;
        });
        // 재실행마다 앞의 예약을 취소하므로, 기본값으로 두면 죽은 노드가 예약 시각까지 큐에 남는다
        executor.setRemoveOnCancelPolicy(true);
        return executor;
    }

    // 저장이 실패하면 큐에도 올리지 않는다. 재시작에 사라질 예약을 성공으로 답하지 않도록.
    void schedule(Reminder reminder) throws SQLException {
        store.save(reminder);
        arm(reminder);
    }

    boolean cancel(long userId) throws SQLException {
        store.delete(userId);
        ScheduledFuture<?> reserved = pending.remove(userId);
        return reserved != null && reserved.cancel(false);
    }

    void restore() throws SQLException {
        for (Reminder reminder : store.findAll()) {
            arm(reminder);
        }
    }

    // 꺼져 있던 사이 지난 예약은 지연이 음수라 즉시 발화한다.
    // 지연 0 이면 태스크가 self[0] 대입보다 먼저 돌 수 있어, 대입과 put 을 태스크의 제거와 같은 락 안에 둔다.
    private synchronized void arm(Reminder reminder) {
        ScheduledFuture<?> replaced = pending.remove(reminder.userId());
        if (replaced != null) {
            replaced.cancel(false);
        }
        long delay = Duration.between(Instant.now(), reminder.fireAt()).toMillis();
        ScheduledFuture<?>[] self = new ScheduledFuture<?>[1];
        self[0] = executor.schedule(() -> fire(reminder, self), delay, TimeUnit.MILLISECONDS);
        pending.put(reminder.userId(), self[0]);
    }

    private void fire(Reminder reminder, ScheduledFuture<?>[] self) {
        synchronized (this) {
            // 이미 시작된 앞 예약이 뒤 예약의 기록을 지우지 않도록 자기 것일 때만 제거
            pending.remove(reminder.userId(), self[0]);
        }
        try {
            store.deleteFired(reminder);
        } catch (SQLException e) {
            // 멘션이 더 중요하다. 남은 행은 다음 기동에서 한 번 더 발화한다.
            log.warn("발화한 예약 삭제 실패 · user={}", reminder.userId(), e);
        }
        // executor 는 태스크 예외를 future 에 담아 삼키므로 여기서 남긴다
        try {
            onFire.accept(reminder);
        } catch (RuntimeException e) {
            log.warn("완충 멘션 실패 · user={} channel={}", reminder.userId(), reminder.channelId(), e);
        }
    }
}
