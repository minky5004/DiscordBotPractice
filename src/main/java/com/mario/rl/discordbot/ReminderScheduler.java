package com.mario.rl.discordbot;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

// 유저당 예약 하나. 메모리에만 두므로 재시작하면 전부 사라진다.
class ReminderScheduler {

    private final ScheduledExecutorService executor = newExecutor();

    private final Map<Long, ScheduledFuture<?>> pending = new ConcurrentHashMap<>();

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

    void schedule(long userId, Duration delay, Runnable onFull) {
        cancel(userId);
        ScheduledFuture<?>[] self = new ScheduledFuture<?>[1];
        // 이미 시작된 앞 예약이 뒤 예약의 기록을 지우지 않도록 자기 것일 때만 제거
        self[0] = executor.schedule(() -> {
            pending.remove(userId, self[0]);
            onFull.run();
        }, delay.toMillis(), TimeUnit.MILLISECONDS);
        pending.put(userId, self[0]);
    }

    boolean cancel(long userId) {
        ScheduledFuture<?> reserved = pending.remove(userId);
        return reserved != null && reserved.cancel(false);
    }
}
