package com.signalpulse.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Bounded thread pool dedicated to blocking outbound I/O during scans.
 * Replaces ScannerService's previous use of parallelStream / common ForkJoinPool,
 * which would have starved other parallel work in the JVM.
 */
@Configuration
public class AppExecutorConfig {

    @Bean(name = "scanExecutor", destroyMethod = "shutdown")
    public ExecutorService scanExecutor(
            @Value("${app.scan.pool-size:8}") int poolSize) {
        int size = Math.max(2, poolSize);
        AtomicInteger seq = new AtomicInteger();
        return Executors.newFixedThreadPool(size, runnable -> {
            Thread t = new Thread(runnable, "scan-" + seq.incrementAndGet());
            t.setDaemon(true);
            return t;
        });
    }
}
