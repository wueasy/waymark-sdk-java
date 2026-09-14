package com.wueasy.waymark.internal.util;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 线程工具：创建带名字的守护线程，避免阻塞 JVM 退出。
 */
public final class Threads {

    private Threads() {
    }

    /** 创建指定前缀的守护线程工厂。 */
    public static ThreadFactory daemonFactory(final String prefix) {
        final AtomicInteger sequence = new AtomicInteger(1);
        return new ThreadFactory() {
            @Override
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable, prefix + "-" + sequence.getAndIncrement());
                thread.setDaemon(true);
                return thread;
            }
        };
    }
}
