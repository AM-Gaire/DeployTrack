package com.deploytrack.config;

import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class AsyncConfig {

    // An explicit, bounded pool rather than Spring's default. The default
    // SimpleAsyncTaskExecutor creates an unbounded thread per task, so a burst
    // of deployments would spawn threads until the JVM runs out of memory.
    // A bounded queue means excess work waits instead of taking the app down.
    @Bean(name = "deploymentExecutor")
    public Executor deploymentExecutor() {
        var executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("deployment-");
        executor.initialize();
        return executor;
    }
}
