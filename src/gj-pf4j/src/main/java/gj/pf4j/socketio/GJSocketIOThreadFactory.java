/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.socketio;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public class GJSocketIOThreadFactory implements ThreadFactory {
    private final String nameFormat;
    private final boolean daemon;
    private final Thread.UncaughtExceptionHandler exceptionHandler;
    private final AtomicInteger threadNumber = new AtomicInteger(1);

    public GJSocketIOThreadFactory(String nameFormat, boolean daemon,
                                   Thread.UncaughtExceptionHandler exceptionHandler) {
        this.nameFormat = nameFormat;
        this.daemon = daemon;
        this.exceptionHandler = exceptionHandler;
    }

    @Override
    public Thread newThread(Runnable runnable) {
        String threadName = String.format(nameFormat, threadNumber.getAndIncrement());
        Thread thread = new Thread(runnable, threadName);
        thread.setDaemon(daemon);
        if (exceptionHandler != null) {
            thread.setUncaughtExceptionHandler(exceptionHandler);
        }
        return thread;
    }

    // Builder pattern
    public static class Builder {
        private String nameFormat;
        private boolean daemon = false;
        private Thread.UncaughtExceptionHandler exceptionHandler;

        public Builder setNameFormat(String nameFormat) {
            this.nameFormat = nameFormat;
            return this;
        }

        public Builder setDaemon(boolean daemon) {
            this.daemon = daemon;
            return this;
        }

        public Builder setUncaughtExceptionHandler(Thread.UncaughtExceptionHandler exceptionHandler) {
            this.exceptionHandler = exceptionHandler;
            return this;
        }

        public GJSocketIOThreadFactory build() {
            if (nameFormat == null) {
                throw new IllegalStateException("nameFormat must be set");
            }
            return new GJSocketIOThreadFactory(nameFormat, daemon, exceptionHandler);
        }
    }
}