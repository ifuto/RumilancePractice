package com.rumilance.practice.util;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Bounded async worker pool used for database I/O and other blocking/heavier work that
 * must not run on the server's main thread. Sized between 1 and 4 threads (default 2)
 * as required by the plugin's operating constraints.
 */
public final class AsyncExecutor implements AutoCloseable {

    public static final int MIN_ALLOWED_THREADS = 1;
    public static final int MAX_ALLOWED_THREADS = 4;
    public static final int DEFAULT_CORE_THREADS = 2;
    public static final int DEFAULT_MAX_THREADS = 4;
    public static final long DEFAULT_KEEP_ALIVE_SECONDS = 60L;

    private final ThreadPoolExecutor executor;

    public AsyncExecutor(int coreThreads, int maxThreads, long keepAliveSeconds) {
        int clampedCore = clamp(coreThreads);
        int clampedMax = Math.max(clampedCore, clamp(maxThreads));
        AtomicInteger counter = new AtomicInteger(1);
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, "RumilancePractice-Worker-" + counter.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        };
        this.executor = new ThreadPoolExecutor(
                clampedCore,
                clampedMax,
                Math.max(0L, keepAliveSeconds),
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(),
                factory
        );
    }

    public static AsyncExecutor withDefaults() {
        return new AsyncExecutor(DEFAULT_CORE_THREADS, DEFAULT_MAX_THREADS, DEFAULT_KEEP_ALIVE_SECONDS);
    }

    private static int clamp(int threads) {
        return Math.max(MIN_ALLOWED_THREADS, Math.min(MAX_ALLOWED_THREADS, threads));
    }

    public <T> CompletableFuture<T> supplyAsync(Supplier<T> supplier) {
        return CompletableFuture.supplyAsync(supplier, executor);
    }

    public CompletableFuture<Void> runAsync(Runnable runnable) {
        return CompletableFuture.runAsync(runnable, executor);
    }

    public void execute(Runnable runnable) {
        executor.execute(runnable);
    }

    public int poolSize() {
        return executor.getPoolSize();
    }

    public int activeThreads() {
        return executor.getActiveCount();
    }

    public long completedTaskCount() {
        return executor.getCompletedTaskCount();
    }

    public void shutdown(long timeoutSeconds) {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(timeoutSeconds, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void close() {
        shutdown(10L);
    }
}
