package com.fampay.scheduler.service;

import com.fampay.scheduler.clients.MetricsClient;
import com.fampay.scheduler.entity.Job;
import com.fampay.scheduler.entity.JobExecution;
import com.fampay.scheduler.enums.JobType;
import com.fampay.scheduler.repository.JobExecutionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.reactive.function.client.*;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class JobExecutorServiceTest {

  @Mock private JobExecutionRepository executionRepository;
  @Mock private WebClient.Builder webClientBuilder;
  @Mock private WebClient webClient;
  @Mock private WebClient.RequestBodyUriSpec requestBodyUriSpec;
  @Mock private WebClient.RequestBodySpec requestBodySpec;
  @Mock private WebClient.RequestHeadersSpec<?> requestHeadersSpec;
  @Mock private WebClient.ResponseSpec responseSpec;
  @Mock private ExecutorService executorService;
  @Mock private MetricsClient metricsClient;

  @Captor private ArgumentCaptor<JobExecution> executionCaptor;

  @InjectMocks private JobExecutorService jobExecutorService;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.initMocks(this);
    when(webClientBuilder.build()).thenReturn(webClient);
    jobExecutorService = new JobExecutorService(
        executionRepository, webClientBuilder, executorService, metricsClient);
  }

  private Job buildJob(JobType type) {
    return Job.builder()
        .id(1L)
        .apiEndpoint("https://test.com/api")
        .type(type.name())
        .build();
  }

  @Test
  void testExecuteJob_SuccessfulExecution() {
    Job job = buildJob(JobType.ATLEAST_ONCE);

    when(webClient.post()).thenReturn(requestBodyUriSpec);
    when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
    when(requestBodySpec.retrieve()).thenReturn(responseSpec);
    when(responseSpec.toBodilessEntity()).thenReturn(Mono.just(ResponseEntity.ok().build()));

    jobExecutorService.executeJob(job);

    verify(executionRepository, timeout(1000)).save(any(JobExecution.class));
    verify(metricsClient).recordJobExecution(eq(1L), eq(job.getType()), eq(200), anyLong());
  }

  @Test
  void testExecuteJob_HttpError() {
    Job job = buildJob(JobType.ATLEAST_ONCE);

    when(webClient.post()).thenReturn(requestBodyUriSpec);
    when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
    when(requestBodySpec.retrieve()).thenReturn(responseSpec);
    when(responseSpec.toBodilessEntity()).thenReturn(Mono.error(new RuntimeException("HTTP 500")));

    jobExecutorService.executeJob(job);

    verify(executionRepository, timeout(1000)).save(any(JobExecution.class));
    verify(metricsClient).recordJobExecutionFailure(eq(1L), eq(job.getType()), contains("HTTP 500"));
  }

  @Test
  void testExecuteJob_ExceptionThrown() {
    Job job = buildJob(JobType.ATLEAST_ONCE);

    when(webClient.post()).thenThrow(new RuntimeException("WebClient error"));

    jobExecutorService.executeJob(job);

    verify(executionRepository).save(any(JobExecution.class));
    verify(metricsClient).recordJobExecutionFailure(eq(1L), eq(job.getType()), contains("WebClient error"));
  }

  @Test
  void testExecuteJob_AtMostOnce_SkipsIfInFlight() {
    Job job = buildJob(JobType.ATMOST_ONCE);

    // First call: should execute
    when(webClient.post()).thenReturn(requestBodyUriSpec);
    when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
    when(requestBodySpec.retrieve()).thenReturn(responseSpec);
    when(responseSpec.toBodilessEntity()).thenReturn(Mono.never()); // never completes

    jobExecutorService.executeJob(job);

    // Second call: should skip due to in-flight
    jobExecutorService.executeJob(job);

    // Only one execution should be attempted
    verify(webClient, times(1)).post();
  }

  @Test
  void testExecuteJob_SkippedDueToSemaphoreLimit() throws InterruptedException {
    Job job = buildJob(JobType.ATLEAST_ONCE);

    // Acquire all permits
    for (int i = 0; i < 1000; i++) {
      assertTrue(jobExecutorService.limit.tryAcquire());
    }

    jobExecutorService.executeJob(job);

    verify(executionRepository).save(executionCaptor.capture());
    JobExecution exec = executionCaptor.getValue();
    assertEquals("execution skipped", exec.getError());

    // Release permits for other tests
    jobExecutorService.limit.release(1000);
  }

  @Test
  void testSaveExecution_ErrorIsHandled() {
    Job job = buildJob(JobType.ATLEAST_ONCE);

    when(webClient.post()).thenThrow(new RuntimeException("fail"));
    doThrow(new RuntimeException("db error")).when(executionRepository).save(any(JobExecution.class));

    // Should not throw
    assertDoesNotThrow(() -> jobExecutorService.executeJob(job));
  }
}