package com.HendrikHoemberg.StudyHelper.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedList;
import java.util.Queue;

/**
 * Service to protect against exceeding the Gemini free tier limit of 15 Requests Per Minute (RPM).
 * Uses a rolling sliding-window queue of request timestamps to suspend threads when limit is reached.
 */
@Service
public class AiThrottlingService {

    private static final Logger log = LoggerFactory.getLogger(AiThrottlingService.class);

    private final int maxRequests;
    private final long windowMs;
    private final long safetyBufferMs;
    private final java.util.function.LongSupplier timeSupplier;

    private final Queue<Long> requestTimestamps = new LinkedList<>();

    /**
     * Default constructor for Spring container dependency injection. Enforces 15 RPM.
     */
    public AiThrottlingService() {
        this(15, 60000, 1000, System::currentTimeMillis);
    }

    /**
     * Testable constructor supporting customized limits and virtual clock suppliers.
     */
    public AiThrottlingService(int maxRequests, long windowMs, long safetyBufferMs, java.util.function.LongSupplier timeSupplier) {
        this.maxRequests = maxRequests;
        this.windowMs = windowMs;
        this.safetyBufferMs = safetyBufferMs;
        this.timeSupplier = timeSupplier;
    }

    /**
     * Blocks/suspends the caller thread if the rolling window contains too many requests.
     * The thread wakes up when the oldest request drops out of the sliding window.
     */
    public void throttle() {
        synchronized (requestTimestamps) {
            long now = timeSupplier.getAsLong();

            // Clean up timestamps older than the sliding window
            while (!requestTimestamps.isEmpty() && now - requestTimestamps.peek() >= windowMs) {
                requestTimestamps.poll();
            }

            if (requestTimestamps.size() >= maxRequests) {
                Long oldest = requestTimestamps.peek();
                if (oldest != null) {
                    long elapsedSinceOldest = now - oldest;
                    long waitTime = windowMs - elapsedSinceOldest + safetyBufferMs;

                    if (waitTime > 0) {
                        log.warn("RPM Limit Protection: {} requests reached. Throttling AI request. Queueing/sleeping thread for {} ms", maxRequests, waitTime);
                        
                        Runnable startCallback = onThrottleStart.get();
                        if (startCallback != null) {
                            try {
                                startCallback.run();
                            } catch (Exception ex) {
                                log.error("Error executing throttle start callback", ex);
                            }
                        }

                        try {
                            Thread.sleep(waitTime);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            log.error("Throttling thread sleep interrupted", e);
                        } finally {
                            Runnable endCallback = onThrottleEnd.get();
                            if (endCallback != null) {
                                try {
                                    endCallback.run();
                                } catch (Exception ex) {
                                    log.error("Error executing throttle end callback", ex);
                                }
                            }
                        }
                    }
                }

                // Re-evaluate 'now' after waiting and clear expired timestamps again
                now = timeSupplier.getAsLong();
                while (!requestTimestamps.isEmpty() && now - requestTimestamps.peek() >= windowMs) {
                    requestTimestamps.poll();
                }
            }

            // Record the current request timestamp
            requestTimestamps.offer(timeSupplier.getAsLong());
        }
    }

    private static final ThreadLocal<Runnable> onThrottleStart = new ThreadLocal<>();
    private static final ThreadLocal<Runnable> onThrottleEnd = new ThreadLocal<>();

    public static void setOnThrottleStart(Runnable callback) {
        onThrottleStart.set(callback);
    }

    public static void setOnThrottleEnd(Runnable callback) {
        onThrottleEnd.set(callback);
    }

    public static void clearCallbacks() {
        onThrottleStart.remove();
        onThrottleEnd.remove();
    }
}
