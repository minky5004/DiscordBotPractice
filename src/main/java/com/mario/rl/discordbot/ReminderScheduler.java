package com.mario.rl.discordbot;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

// 유저당 예약 하나. 메모리에만 두므로 재시작하면 전부 사라진다.
class ReminderScheduler {

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(task -> {
        // JDA 가 내려간 뒤 JVM 이 이 스레드에 붙들리지 않게 데몬으로
        Thread thread = new Thread(task, "enkephalin-reminder");
        thread.setDaemon(true);
        return thread;
    });

    private final Map<Long, ScheduledFuture<?>> pending = new ConcurrentHashMap<>();

    void schedule(long userId, Duration delay, Runnable onFull) {
        cancel(userId);
        pending.put(userId, executor.schedule(() -> {
            pending.remove(userId);
            onFull.run();
        }, delay.toMillis(), TimeUnit.MILLISECONDS));
    }

    boolean cancel(long userId) {
        ScheduledFuture<?> reserved = pending.remove(userId);
        return reserved != null && reserved.cancel(false);
    }
}
