package com.fampay.scheduler.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fampay.scheduler.enums.JobStatus;
import com.fampay.scheduler.enums.JobType;
import com.fampay.scheduler.entity.Job;
import com.fampay.scheduler.exception.InvalidReqParameters;
import com.fampay.scheduler.exception.JobNotFoundException;
import com.fampay.scheduler.models.JobExecution;
import com.fampay.scheduler.models.JobSpec;
import com.fampay.scheduler.repository.JobExecutionRepository;
import com.fampay.scheduler.repository.JobsRepository;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class JobServiceTest {

  @Mock private JobsRepository jobsRepository;

  @Mock private JobExecutionRepository jobExecutionRepository;

  @Mock private JobSchedulerService schedulerService;

  @InjectMocks private JobServiceImpl jobService;

  private JobSpec jobSpec;
  private Job job;
  private Long jobId;

  @BeforeEach
  void setUp() {
    jobId = 1L;
    jobSpec = new JobSpec();
    jobSpec.setSchedule("31 10-15 1 * * MON-FRI");
    jobSpec.setApi("https://localhost:4444/foo");
    jobSpec.setType(JobType.ATLEAST_ONCE);

    job = Job.builder().id(jobId).schedule(jobSpec.getSchedule()).apiEndpoint(jobSpec.getApi())
        .type(jobSpec.getType().name()).status(JobStatus.ACTIVE).build();
  }

  @Test
  void testCreateJobFromSpec_Success() {
    when(jobsRepository.save(any(Job.class))).thenReturn(job);
    doNothing().when(schedulerService).validateCronExpression(anyString());
    doNothing().when(schedulerService).scheduleJob(any(Job.class));

    Long result = jobService.createJobFromSpec(jobSpec);

    assertNotNull(result);
    assertEquals(jobId, result);
    verify(jobsRepository, times(1)).save(any(Job.class));
    verify(schedulerService, times(1)).scheduleJob(any(Job.class));
  }

  @Test
  void testCreateJobFromSpec_InvalidSchedule() {
    jobSpec.setSchedule("");

    assertThrows(
        InvalidReqParameters.class, () -> jobService.createJobFromSpec(jobSpec));
  }

  @Test
  void testCreateJobFromSpec_InvalidApi() {
    jobSpec.setApi("");

    assertThrows(
        InvalidReqParameters.class, () -> jobService.createJobFromSpec(jobSpec));
  }

  @Test
  void testCreateJobFromSpec_NullType() {
    jobSpec.setType(null);

    assertThrows(
        InvalidReqParameters.class, () -> jobService.createJobFromSpec(jobSpec));
  }

  @Test
  void testGetExecutions_Success() {
    when(jobsRepository.findById(jobId)).thenReturn(Optional.of(job));

    LocalDateTime now = LocalDateTime.now();
    com.fampay.scheduler.entity.JobExecution exec1 =
        com.fampay.scheduler.entity.JobExecution.builder()
            .jobId(jobId)
            .runAt(now)
            .httpStatus(200)
            .timeTakenMs(100L)
            .build();

    com.fampay.scheduler.entity.JobExecution exec2 =
        com.fampay.scheduler.entity.JobExecution.builder()
            .jobId(jobId)
            .runAt(now.minusMinutes(1))
            .httpStatus(500)
            .timeTakenMs(5000L)
            .error("Timeout")
            .build();

    List<com.fampay.scheduler.entity.JobExecution> executions = Arrays.asList(exec1, exec2);
    Pageable pageable = PageRequest.of(0, 10);

    when(jobExecutionRepository.findByJobIdOrderByRunAtDesc(jobId, pageable))
        .thenReturn(executions);

    List<JobExecution> result = jobService.getExecutions(jobId.toString(), pageable);

    assertNotNull(result);
    assertEquals(2, result.size());
    assertEquals(200, result.get(0).getHttpStatus());
    assertEquals(100L, result.get(0).getTimeTakenMs());
    assertEquals(500, result.get(1).getHttpStatus());
    assertEquals("Timeout", result.get(1).getError());
  }

  @Test
  void testGetExecutions_WithCustomLimit() {
    when(jobsRepository.findById(jobId)).thenReturn(Optional.of(job));

    LocalDateTime now = LocalDateTime.now();
    com.fampay.scheduler.entity.JobExecution exec1 =
        com.fampay.scheduler.entity.JobExecution.builder()
            .jobId(jobId)
            .runAt(now)
            .httpStatus(200)
            .timeTakenMs(100L)
            .build();

    List<com.fampay.scheduler.entity.JobExecution> executions = Arrays.asList(exec1);
    Pageable pageable = PageRequest.of(0, 5);

    when(jobExecutionRepository.findByJobIdOrderByRunAtDesc(jobId, pageable))
        .thenReturn(executions);

    List<JobExecution> result = jobService.getExecutions(jobId.toString(), pageable);

    assertNotNull(result);
    assertEquals(1, result.size());
    assertEquals(200, result.get(0).getHttpStatus());
  }

  @Test
  void testGetExecutions_JobNotFound() {
    when(jobsRepository.findById(jobId)).thenReturn(Optional.empty());
    Pageable pageable = PageRequest.of(0, 10);

    assertThrows(
        JobNotFoundException.class,
        () -> jobService.getExecutions(jobId.toString(), pageable));
  }

  @Test
  void testGetExecutions_InvalidJobIdFormat() {
    Pageable pageable = PageRequest.of(0, 10);

    assertThrows(
        InvalidReqParameters.class, () -> jobService.getExecutions("invalid", pageable));
  }
}

