package com.fampay.scheduler.service;

import com.fampay.scheduler.clients.MetricsClient;
import com.fampay.scheduler.enums.JobType;
import com.fampay.scheduler.entity.Job;
import com.fampay.scheduler.entity.JobExecution;
import com.fampay.scheduler.repository.JobExecutionRepository;
import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.util.retry.Retry;

@Slf4j
@Service
public class JobExecutorService {

  private final JobExecutionRepository executionRepository;
  private final WebClient webClient;
  private final ExecutorService executorService;
  private final MetricsClient metricsClient;
  Semaphore limit = new Semaphore(1000); // max concurrent calls


  // Track in-flight executions for AT_MOST_ONCE semantics
  private final ConcurrentHashMap<Long, CompletableFuture<Void>> inFlightExecutions =
      new ConcurrentHashMap<>();

  @Autowired
  public JobExecutorService(
      JobExecutionRepository executionRepository,
      WebClient.Builder webClientBuilder,
      @Qualifier("jobExecutor") ExecutorService executorService,
      MetricsClient metricsClient) {
    this.executionRepository = executionRepository;
    this.webClient = webClientBuilder.build();
    this.executorService = executorService;
    this.metricsClient = metricsClient;
  }

  /**
   * Executes a job by making an HTTP POST request to the configured API endpoint.
   *
   * @param job The job to execute
   */
  public void executeJob(Job job) {
    JobType jobType = JobType.valueOf(job.getType().toUpperCase());
    Long jobId = job.getId();

    // For AT_MOST_ONCE, check if execution is already in flight
    if (jobType == JobType.ATMOST_ONCE) {
      if (inFlightExecutions.containsKey(jobId)) {
        log.debug("Job {} already has an execution in flight, skipping (AT_MOST_ONCE)", jobId);
        return;
      }
    }

    LocalDateTime executionTime = LocalDateTime.now();
    long startTime = System.currentTimeMillis();

    // For AT_MOST_ONCE, mark execution as in-flight
    final CompletableFuture<Void> executionFuture;
    if (jobType == JobType.ATMOST_ONCE) {
      executionFuture = new CompletableFuture<>();
      inFlightExecutions.put(jobId, executionFuture);
    } else {
      executionFuture = null;
    }

    try {
      log.info("Executing job {} - POST to {}", jobId, job.getApiEndpoint());

      // Make HTTP POST request with timeout
      if (limit.tryAcquire()) {
        webClient
            .post()
            .uri(job.getApiEndpoint())
            .retrieve()
            .toBodilessEntity()
            .timeout(java.time.Duration.ofSeconds(90)) // Max timeout as per requirement
            .doFinally(signal -> limit.release())
            .subscribe(
                response -> {
                  long timeTaken = System.currentTimeMillis() - startTime;
                  Integer statusCode = response.getStatusCode().value();
                  saveExecution(jobId, executionTime, statusCode, timeTaken, null);

                  // Record metrics
                  metricsClient.recordJobExecution(jobId, job.getType(), statusCode, timeTaken);

                  log.info(
                      "Job {} executed successfully. Status: {}, Time: {}ms",
                      jobId,
                      statusCode,
                      timeTaken);
                  if (executionFuture != null) {
                    executionFuture.complete(null);
                  }
                },
                error -> {
                  long timeTaken = System.currentTimeMillis() - startTime;
                  String errorMessage = error.getMessage();
                  if (errorMessage == null || errorMessage.length() > 2048) {
                    errorMessage =
                        error.getClass().getSimpleName()
                            + (errorMessage != null
                            ? ": " + errorMessage.substring(0, Math.min(2048, errorMessage.length()))
                            : "");
                  }
                  saveExecution(
                      jobId, executionTime, null, timeTaken, errorMessage);

                  // Record failure metrics
                  metricsClient.recordJobExecutionFailure(jobId, job.getType(), errorMessage);

                  log.error("Job {} execution failed: {}", jobId, errorMessage, error);
                  if (executionFuture != null) {
                    executionFuture.completeExceptionally(error);
                  }
                });
      } else {
        long timeTaken = System.currentTimeMillis() - startTime;
        log.info("skipping execution");
        saveExecution(
            jobId, executionTime, null, timeTaken, "execution skipped");
      }


    } catch (Exception e) {
      long timeTaken = System.currentTimeMillis() - startTime;
      String errorMessage = e.getMessage();
      saveExecution(jobId, executionTime, null, timeTaken, errorMessage);
      
      // Record failure metrics
      metricsClient.recordJobExecutionFailure(jobId, job.getType(), errorMessage);
      
      log.error("Error executing job {}: {}", jobId, e.getMessage(), e);
      if (executionFuture != null) {
        executionFuture.completeExceptionally(e);
      }
    } finally {
      // For AT_MOST_ONCE, remove from in-flight after a delay to allow completion
      if (jobType == JobType.ATMOST_ONCE && executionFuture != null) {
        executionFuture.whenComplete(
            (result, throwable) -> {
              // Remove after completion
              inFlightExecutions.remove(jobId);
            });
      }
    }
  }

  /**
   * Save execution record to database to avoid blocking.
   *
   * @param jobId Job ID
   * @param runAt Execution timestamp
   * @param httpStatus HTTP status code
   * @param timeTakenMs Time taken in milliseconds
   * @param error Error message if any
   */
  private void saveExecution(
      Long jobId,
      LocalDateTime runAt,
      Integer httpStatus,
      Long timeTakenMs,
      String error) {
      try {
        JobExecution execution =
            JobExecution.builder()
                .jobId(jobId)
                .runAt(runAt)
                .httpStatus(httpStatus)
                .timeTakenMs(timeTakenMs)
                .error(error)
                .build();
        executionRepository.save(execution);
      } catch (Exception e) {
        log.error("Error saving execution record for job {}: {}", jobId, e.getMessage(), e);
      }
  }
}
