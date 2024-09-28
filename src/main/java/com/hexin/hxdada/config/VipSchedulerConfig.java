package com.hexin.hxdada.config;

import io.reactivex.Scheduler;
import io.reactivex.schedulers.Schedulers;
import lombok.Data;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * vip用户/管理员线程配置类
 */
@Configuration
@Data
public class VipSchedulerConfig {

    @Bean
    public Scheduler vipScheduler() {
        // 创建线程工厂
        ThreadFactory threadFactory = new ThreadFactory() {
            // 在内部，使用 AtomicInteger 来追踪线程编号，每创建一个新线程，编号自动递增。
            private final AtomicInteger threadNumber = new AtomicInteger(1);

            // 创建线程
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "VIPThreadPool-" + threadNumber.getAndIncrement());
                t.setDaemon(false); // 设置为非守护线程
                return t;
            }
        };

        // 参数1表示线程数，参数2表示线程工厂
        ExecutorService executorService = Executors.newScheduledThreadPool(10, threadFactory);
        return Schedulers.from(executorService);
    }
}
