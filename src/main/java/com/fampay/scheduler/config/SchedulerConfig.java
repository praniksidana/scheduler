package com.fampay.scheduler.config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@EnableScheduling
public class SchedulerConfig {

  /** Thread pool for job execution - can handle 1000s of concurrent jobs */
  @Bean(name = "jobExecutor")
  public ExecutorService jobExecutor() {
    int corePoolSize = Runtime.getRuntime().availableProcessors() * 4;
    int maxPoolSize = 1000; // Support thousands of concurrent jobs
    return new java.util.concurrent.ThreadPoolExecutor(
        corePoolSize,
        maxPoolSize,
        60L,
        TimeUnit.SECONDS,
        new java.util.concurrent.LinkedBlockingQueue<>(10000),
        new ThreadFactoryBuilder().setNameFormat("job-executor-%d").build(),
        new ThreadPoolExecutor.CallerRunsPolicy());
  }

  /** Thread pool for Sample job execution - can handle 1000s of concurrent jobs */
  @Bean(name = "sampleJobExecutor")
  public ExecutorService sampleJobExecutor() {
    int corePoolSize = 1000;
    int maxPoolSize = 1000; // Support thousands of concurrent jobs
    return new java.util.concurrent.ThreadPoolExecutor(
        corePoolSize,
        maxPoolSize,
        60L,
        TimeUnit.SECONDS,
        new java.util.concurrent.LinkedBlockingQueue<>(10000),
        new ThreadFactoryBuilder().setNameFormat("job-executor-%d").build(),
        new ThreadPoolExecutor.CallerRunsPolicy());
  }

  /** Scheduled executor for CRON-based scheduling */
  @Bean(name = "schedulerExecutor")
  public ScheduledExecutorService schedulerExecutor() {
    int threads = Runtime.getRuntime().availableProcessors() * 2;
    return Executors.newScheduledThreadPool(
        threads, new ThreadFactoryBuilder().setNameFormat("scheduler-%d").build());
  }

  /** WebClient for making HTTP requests */
  @Bean
  @Primary
  public WebClient.Builder webClientBuilder() {
    return WebClient.builder()
        .clientConnector(
            new org.springframework.http.client.reactive.ReactorClientHttpConnector(
                reactor.netty.http.client.HttpClient.create()
                    .responseTimeout(java.time.Duration.ofSeconds(90))
                    .option(
                        io.netty.channel.ChannelOption.CONNECT_TIMEOUT_MILLIS, 30000)));
  }

  /** Helper class for thread factory */
  private static class ThreadFactoryBuilder {
    private String nameFormat;

    public ThreadFactoryBuilder setNameFormat(String nameFormat) {
      this.nameFormat = nameFormat;
      return this;
    }

    public java.util.concurrent.ThreadFactory build() {
      return r -> {
        Thread t = new Thread(r);
        if (nameFormat != null) {
          t.setName(String.format(nameFormat, t.getId()));
        }
        t.setDaemon(false);
        return t;
      };
    }
  }
}
