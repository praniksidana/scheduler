package com.fampay.scheduler.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.cronutils.model.time.ExecutionTime;
import com.fampay.scheduler.clients.MetricsClient;
import com.fampay.scheduler.config.LeaderLock;
import com.fampay.scheduler.entity.Job;
import com.fampay.scheduler.entity.ScheduledJob;
import com.fampay.scheduler.enums.JobStatus;
import com.fampay.scheduler.exception.InvalidReqParameters;
import com.fampay.scheduler.repository.JobsRepository;
import com.fampay.scheduler.repository.JobExecutionRepository;
import com.fampay.scheduler.repository.ScheduledJobRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JobSchedulerServiceTest {

  @InjectMocks private JobSchedulerService jobSchedulerService;
  @Mock JobsRepository jobsRepository;
  @Mock
  ScheduledJobRepository scheduledJobRepository;
  @Mock JobExecutorService executorService;
  @Mock ScheduledExecutorService schedulerExecutor;
  @Mock
  ExecutorService jobExecutor;
  @Mock
  MetricsClient metricsClient;
  @Mock
  LeaderLock leaderLock;
  private Job activeJob;
  private Job inactiveJob;
  private ScheduledJob scheduledJob;
  @BeforeEach
  void setUp() {
    activeJob = Job.builder()
        .id(1L)
        .schedule("0 * * * * ?")
        .apiEndpoint("https://test.com/api")
        .type("ATLEAST_ONCE")
        .status(JobStatus.ACTIVE)
        .build();

    inactiveJob = Job.builder()
        .id(2L)
        .schedule("0 * * * * ?")
        .apiEndpoint("https://test.com/api")
        .type("ATLEAST_ONCE")
        .status(JobStatus.INACTIVE)
        .build();

    scheduledJob = ScheduledJob.builder()
        .jobId(1L)
        .nextRunTime(LocalDateTime.now().plusMinutes(1))
        .isActive(true)
        .scheduleVersion(1L)
        .build();
  }

  @Test
  void testValidateCronExpression_ValidExpression_NoException() {
    assertDoesNotThrow(() -> jobSchedulerService.validateCronExpression("0 * * * * ?"));
  }

  @Test
  void testValidateCronExpression_InvalidExpression_ThrowsException() {
    assertThrows(
        Exception.class, () -> jobSchedulerService.validateCronExpression("invalid cron"));
  }
  @Test
  void testScheduleJob_ActiveJob_SchedulesAndSaves() {
    when(scheduledJobRepository.findByJobId(1L)).thenReturn(Optional.empty());
    jobSchedulerService.scheduleJob(activeJob);
    verify(scheduledJobRepository).save(any(ScheduledJob.class));
  }

  @Test
  void testScheduleJob_InactiveJob_Deactivates() {
    jobSchedulerService.scheduleJob(inactiveJob);
    verify(scheduledJobRepository).deleteByJobId(2L);
  }

  @Test
  void testScheduleJob_InvalidCron_LogsError() {
    Job job = Job.builder().id(3L).schedule("invalid cron").status(JobStatus.ACTIVE).build();
    jobSchedulerService.scheduleJob(job);
    // Should not throw, just log error
    verify(scheduledJobRepository, never()).save(any());
  }

  @Test
  void testCancelScheduledTask_Deletes() {
    jobSchedulerService.cancelScheduledTask(1L);
    verify(scheduledJobRepository).deleteByJobId(1L);
  }

  @Test
  void testValidateCronExpression_Valid() {
    assertDoesNotThrow(() -> jobSchedulerService.validateCronExpression("0 * * * * ?"));
  }

  @Test
  void testValidateCronExpression_Invalid_Throws() {
    assertThrows(InvalidReqParameters.class, () -> jobSchedulerService.validateCronExpression("bad cron"));
  }

  @Test
  void testPollAndExecuteReadyJobs_NotLeader_DoesNothing() {
    when(leaderLock.acquire(anyString(), anyInt())).thenReturn(false);
    jobSchedulerService.pollAndExecuteReadyJobs();
    verifyNoInteractions(scheduledJobRepository, jobsRepository, executorService);
  }

  @Test
  void testPollAndExecuteReadyJobs_OrphanedSchedule_Deletes() {
    when(leaderLock.acquire(anyString(), anyInt())).thenReturn(true);
    when(scheduledJobRepository.findReadyToExecute(any())).thenReturn(List.of(scheduledJob));
    when(jobsRepository.findById(1L)).thenReturn(Optional.empty());
    jobSchedulerService.pollAndExecuteReadyJobs();
    verify(scheduledJobRepository).delete(scheduledJob);
  }

  @Test
  void testPollAndExecuteReadyJobs_InactiveJob_Deletes() {
    when(leaderLock.acquire(anyString(), anyInt())).thenReturn(true);
    when(scheduledJobRepository.findReadyToExecute(any())).thenReturn(List.of(scheduledJob));
    when(jobsRepository.findById(1L)).thenReturn(Optional.of(inactiveJob));
    jobSchedulerService.pollAndExecuteReadyJobs();
    verify(scheduledJobRepository).deleteByJobId(anyLong());
  }

  @Test
  void testPollAndExecuteReadyJobs_Exception_Handled() {
    when(leaderLock.acquire(anyString(), anyInt())).thenReturn(true);
    when(scheduledJobRepository.findReadyToExecute(any())).thenThrow(new RuntimeException("fail"));
    assertDoesNotThrow(() -> jobSchedulerService.pollAndExecuteReadyJobs());
  }

  @Test
  void testScanAndScheduleJobs_Leader_SchedulesMissing() {
    when(leaderLock.acquire(anyString(), anyInt())).thenReturn(true);
    when(jobsRepository.findAll()).thenReturn(List.of(activeJob, inactiveJob));
    when(scheduledJobRepository.findByJobId(1L)).thenReturn(Optional.empty());
    when(scheduledJobRepository.count()).thenReturn(1L);
    jobSchedulerService.scanAndScheduleJobs();
    verify(scheduledJobRepository).save(any(ScheduledJob.class));
    verify(metricsClient).updateScheduledJobsGauge(1);
    verify(metricsClient).updateActiveJobsGauge(1);
  }

  @Test
  void testScanAndScheduleJobs_NotLeader_DoesNothing() {
    when(leaderLock.acquire(anyString(), anyInt())).thenReturn(false);
    jobSchedulerService.scanAndScheduleJobs();
    verifyNoInteractions(jobsRepository, scheduledJobRepository, metricsClient);
  }

  @Test
  void testScanAndScheduleJobs_Exception_Handled() {
    when(leaderLock.acquire(anyString(), anyInt())).thenReturn(true);
    when(jobsRepository.findAll()).thenThrow(new RuntimeException("fail"));
    assertDoesNotThrow(() -> jobSchedulerService.scanAndScheduleJobs());
  }

  @Test
  void testInitializeScheduledJobs_NotLeader_DoesNothing() {
    when(leaderLock.acquire(anyString(), anyInt())).thenReturn(false);
    jobSchedulerService.initializeScheduledJobs();
    verifyNoInteractions(jobsRepository, scheduledJobRepository);
  }

  @Test
  void testCleanupScheduledJobs_Leader_CleansAll() {
    when(leaderLock.acquire(anyString(), anyInt())).thenReturn(true);
    when(scheduledJobRepository.deleteInactiveSchedules(any())).thenReturn(1);
    when(scheduledJobRepository.deleteStaleSchedules(any())).thenReturn(2);
    when(scheduledJobRepository.findOrphanedSchedules()).thenReturn(List.of(scheduledJob));
    jobSchedulerService.cleanupScheduledJobs();
    verify(scheduledJobRepository).deleteInactiveSchedules(any());
    verify(scheduledJobRepository).deleteStaleSchedules(any());
    verify(scheduledJobRepository).delete(scheduledJob);
  }

  @Test
  void testCleanupScheduledJobs_NotLeader_DoesNothing() {
    when(leaderLock.acquire(anyString(), anyInt())).thenReturn(false);
    jobSchedulerService.cleanupScheduledJobs();
    verifyNoInteractions(scheduledJobRepository);
  }

  @Test
  void testCleanupScheduledJobs_Exception_Handled() {
    when(leaderLock.acquire(anyString(), anyInt())).thenReturn(true);
    when(scheduledJobRepository.deleteInactiveSchedules(any())).thenThrow(new RuntimeException("fail"));
    assertDoesNotThrow(() -> jobSchedulerService.cleanupScheduledJobs());
  }
}
