package com.HendrikHoemberg.StudyHelper.controller;

import com.HendrikHoemberg.StudyHelper.service.SavedSessionService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DungeonSessionConcurrencyTests {

    @Test
    void withSessionSerializesConcurrentBodiesOnSameSession() throws Exception {
        MockHttpSession session = new MockHttpSession();
        SavedSessionService saved = mock(SavedSessionService.class);
        when(saved.loadDungeon(any())).thenReturn(Optional.empty());

        AtomicBoolean inSection = new AtomicBoolean(false);
        AtomicBoolean overlap = new AtomicBoolean(false);
        AtomicInteger counter = new AtomicInteger(0);
        int threads = 8;
        int iterations = 200;

        Runnable task = () -> {
            for (int i = 0; i < iterations; i++) {
                DungeonControllerAccess.withSession(session, null, saved, state -> {
                    if (!inSection.compareAndSet(false, true)) overlap.set(true);
                    int v = counter.get();
                    try { Thread.sleep(0, 50_000); } catch (InterruptedException ignored) {}
                    counter.set(v + 1);
                    if (!inSection.compareAndSet(true, false)) overlap.set(true);
                    return "ok";
                });
            }
        };

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int t = 0; t < threads; t++) futures.add(pool.submit(task));
            for (Future<?> f : futures) f.get(20, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }

        assertThat(overlap.get()).as("critical sections must never overlap").isFalse();
        assertThat(counter.get()).as("no lost updates").isEqualTo(threads * iterations);
    }

    @Test
    void withSessionLockAndWithSessionShareTheSameMutex() throws Exception {
        MockHttpSession session = new MockHttpSession();
        SavedSessionService saved = mock(SavedSessionService.class);
        when(saved.loadDungeon(any())).thenReturn(Optional.empty());

        CountDownLatch lockHeld = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean contenderEntered = new AtomicBoolean(false);

        Thread holder = new Thread(() -> DungeonControllerAccess.withSessionLock(session, () -> {
            lockHeld.countDown();
            try { release.await(5, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}
            return "held";
        }));
        holder.start();
        assertThat(lockHeld.await(5, TimeUnit.SECONDS)).isTrue();

        Thread contender = new Thread(() ->
            DungeonControllerAccess.withSession(session, null, saved, state -> {
                contenderEntered.set(true);
                return "in";
            }));
        contender.start();

        // While the holder keeps the lock, the contender must not enter.
        Thread.sleep(150);
        boolean enteredWhileLocked = contenderEntered.get();

        release.countDown();
        holder.join(5000);
        contender.join(5000);

        assertThat(enteredWhileLocked).as("contender blocked while lock held").isFalse();
        assertThat(contenderEntered.get()).as("contender entered after release").isTrue();
    }
}
