/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.socketio;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

@Component
public class GJHubSocketConnectionRateLimiter {
    private final long maxConnectionsPerSecond;
    private final AtomicLong counter = new AtomicLong(0);
    private volatile long lastResetTime = System.currentTimeMillis();

    public GJHubSocketConnectionRateLimiter(GJSocketIOConfig socketIOConfig) {
        this.maxConnectionsPerSecond = socketIOConfig.getMaxConnectionsPerSecond();
    }

    public boolean tryAcquire() {
        long now = System.currentTimeMillis();
        long resetTime = this.lastResetTime;
        if (now - resetTime < 1000) {
            long current = counter.get();
            if (current >= maxConnectionsPerSecond) {
                return false;
            }
            while (!counter.compareAndSet(current, current + 1)) {
                current = counter.get();
                if (current >= maxConnectionsPerSecond) {
                    return false;
                }
            }
            return true;
        }
        return tryAcquireWithReset(now);
    }

    private synchronized boolean tryAcquireWithReset(long now) {
        int PER_SECOND = 1000;
        if (now - lastResetTime >= PER_SECOND) {
            counter.set(0);
            lastResetTime = now;
        }
        if (counter.get() < maxConnectionsPerSecond) {
            counter.incrementAndGet();
            return true;
        }
        return false;
    }
}