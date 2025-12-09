package com.fampay.scheduler.service;

import com.cronutils.model.Cron;
import com.cronutils.model.CronType;
import com.cronutils.model.definition.CronDefinition;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.model.time.ExecutionTime;
import com.cronutils.parser.CronParser;
import com.fampay.scheduler.clients.MetricsClient;
import com.fampay.scheduler.config.LeaderLock;
import com.fampay.scheduler.entity.Job;
import com.fampay.scheduler.entity.ScheduledJob;
import com.fampay.scheduler.enums.JobStatus;
import com.fampay.scheduler.exception.InvalidReqParameters;
import com.fampay.scheduler.repository.JobsRepository;
import com.fampay.scheduler.repository.ScheduledJobRepository;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class JobSchedulerService {

  private static final CronDefinition CRON_DEFINITION =
      CronDefinitionBuilder.instanceDefinitionFor(CronType.QUARTZ);
  private static final CronParser PARSER = new CronParser(CRON_DEFINITION);

  private final JobsRepository jobsRepository;
  private final ScheduledJobRepository scheduledJobRepository;
  private final JobExecutorService executorService;
  private final ScheduledExecutorService schedulerExecutor;
  private final ExecutorService jobExecutor;
  private final MetricsClient metricsClient;
  private final LeaderLock leaderLock;

  // Track version for schedule changes to invalidate old scheduled entries
  private static final java.util.concurrent.atomic.AtomicLong scheduleVersionGenerator =
      new java.util.concurrent.atomic.AtomicLong(System.currentTimeMillis());

  @Autowired
  public JobSchedulerService(
      JobsRepository jobsRepository,
      ScheduledJobRepository scheduledJobRepository,
      JobExecutorService executorService,
      @Qualifier("schedulerExecutor") ScheduledExecutorService schedulerExecutor,
      @Qualifier("jobExecutor") ExecutorService jobExecutor,
      MetricsClient metricsClient,
      LeaderLock leaderLock) {
    this.jobsRepository = jobsRepository;
    this.scheduledJobRepository = scheduledJobRepository;
    this.executorService = executorService;
    this.schedulerExecutor = schedulerExecutor;
    this.jobExecutor = jobExecutor;
    this.metricsClient = metricsClient;
    this.leaderLock = leaderLock;
  }

  /**
   * Schedules a job based on its CRON expression. Stores the schedule in database for persistence
   * and scalability.
   */
  @Transactional
  public void scheduleJob(Job job) {
    if (JobStatus.ACTIVE != job.getStatus()) {
      log.info("Job {} is not active, deactivating schedule", job.getId());
      deactivateScheduledJob(job.getId());
      return;
    }

    try {
      Cron cron = PARSER.parse(job.getSchedule());
      ExecutionTime executionTime = ExecutionTime.forCron(cron);

      // Increment version to invalidate any old scheduled entries
      long currentVersion = scheduleVersionGenerator.incrementAndGet();

      // Calculate next execution time
      ZonedDateTime now = ZonedDateTime.now();
      Optional<ZonedDateTime> nextExecution = executionTime.nextExecution(now);

      if (nextExecution.isPresent()) {
        ZonedDateTime nextRun = nextExecution.get();
        LocalDateTime nextRunLocal = nextRun.toLocalDateTime();

        // Save or update scheduled job in database
        Optional<ScheduledJob> existing = scheduledJobRepository.findByJobId(job.getId());
        ScheduledJob scheduledJob;
        if (existing.isPresent()) {
          scheduledJob = existing.get();
          scheduledJob.setNextRunTime(nextRunLocal);
          scheduledJob.setIsActive(true);
          scheduledJob.setScheduleVersion(currentVersion);
        } else {
          scheduledJob =
              ScheduledJob.builder()
                  .jobId(job.getId())
                  .nextRunTime(nextRunLocal)
                  .isActive(true)
                  .scheduleVersion(currentVersion)
                  .build();
        }

        scheduledJobRepository.save(scheduledJob);

        long delayMillis = java.time.Duration.between(now, nextRun).toMillis();

        if (delayMillis <= 0) {
          // Should run immediately
          log.info("Job {} scheduled to run immediately", job.getId());
          executeJobAsync(job);
          // Schedule next execution will be picked up by the poller
        } else {
          log.info(
              "Job {} scheduled to run at {} ({}ms delay)",
              job.getId(),
              nextRun,
              delayMillis);

//           For short delays, use ScheduledExecutorService for immediate scheduling
//           For longer delays, rely on the database poller
          if (delayMillis < 60000) { // If less than 1 minute, use in-memory scheduler
            schedulerExecutor.schedule(
                () -> {
                  // Verify the schedule version is still current before executing
                  Optional<ScheduledJob> scheduled =
                      scheduledJobRepository.findByJobIdAndVersion(job.getId(), currentVersion);
                  if (scheduled.isPresent() && scheduled.get().getIsActive()) {
                    executeJobAsync(job);
                    scheduleNextExecution(job, executionTime, currentVersion);
                  }
                },
                delayMillis,
                TimeUnit.MILLISECONDS);
          }
          // For longer delays, the poller will pick it up
        }
      } else {
        log.warn("No next execution time found for job {}", job.getId());
        deactivateScheduledJob(job.getId());
      }
    } catch (Exception e) {
      log.error("Error scheduling job {}: {}", job.getId(), e.getMessage(), e);
    }
  }

  /**
   * Schedules the next execution for a job. This is called after each execution to continuously
   * schedule future runs.
   */
  @Transactional
  private void scheduleNextExecution(Job job, ExecutionTime executionTime, long scheduleVersion) {
    if (JobStatus.ACTIVE != job.getStatus()) {
      deactivateScheduledJob(job.getId());
      return;
    }

    // Verify version is still current
    Optional<ScheduledJob> scheduled =
        scheduledJobRepository.findByJobIdAndVersion(job.getId(), scheduleVersion);
    if (scheduled.isEmpty()) {
      log.debug("Schedule version changed for job {}, skipping next execution", job.getId());
      return; // Schedule was updated, let the new schedule handle it
    }

    ZonedDateTime now = ZonedDateTime.now();
    Optional<ZonedDateTime> nextExecution = executionTime.nextExecution(now);

    if (nextExecution.isPresent()) {
      ZonedDateTime nextRun = nextExecution.get();
      LocalDateTime nextRunLocal = nextRun.toLocalDateTime();
      long delayMillis = java.time.Duration.between(now, nextRun).toMillis();

      if (delayMillis > 0) {
        // Update next run time in database
        ScheduledJob scheduledJob = scheduled.get();
        scheduledJob.setNextRunTime(nextRunLocal);
        scheduledJob.setIsActive(true);
        scheduledJobRepository.save(scheduledJob);

        log.debug("Next execution for job {} scheduled at {}", job.getId(), nextRun);

        // For short delays, schedule immediately
        if (delayMillis < 60000) {
          schedulerExecutor.schedule(
              () -> {
                Optional<ScheduledJob> currentSchedule =
                    scheduledJobRepository.findByJobIdAndVersion(job.getId(), scheduleVersion);
                if (currentSchedule.isPresent() && currentSchedule.get().getIsActive()) {
                  executeJobAsync(job);
                  scheduleNextExecution(job, executionTime, scheduleVersion);
                }
              },
              delayMillis,
              TimeUnit.MILLISECONDS);
        }
        // For longer delays, the poller will pick it up
      }
    }
  }

  /** Execute job asynchronously without blocking the scheduler thread. */
  private void executeJobAsync(Job job) {
    CompletableFuture.runAsync(
        () -> {
          try {
            executorService.executeJob(job);
          } catch (Exception e) {
            log.error("Error executing job {}: {}", job.getId(), e.getMessage(), e);
          }
        },
        jobExecutor);
  }

  /** Deactivate scheduled job in database */
  @Transactional
  public void cancelScheduledTask(Long jobId) {
    // Delete immediately instead of deactivating to prevent bloat
    scheduledJobRepository.deleteByJobId(jobId);
    log.info("Cancelled and removed scheduled task for job {}", jobId);
  }

  private void deactivateScheduledJob(Long jobId) {
    // Delete instead of just deactivating to prevent table bloat
    scheduledJobRepository.deleteByJobId(jobId);
    log.debug("Removed scheduled job for jobId {}", jobId);
  }

  /**
   * Validates a CRON expression (6-field format with seconds).
   *
   * @param cronExpression CRON expression to validate
   * @throws InvalidReqParameters if the expression is invalid
   */
  public void validateCronExpression(String cronExpression) {
    try {
      PARSER.parse(cronExpression);
    } catch (Exception e) {
      throw new InvalidReqParameters(
          "Invalid CRON expression: " + cronExpression + ". Error: " + e.getMessage());
    }
  }

  /**
   * Polls database for jobs ready to execute. This replaces the in-memory map and makes the system
   * scalable across multiple instances.
   */
  @Scheduled(fixedDelay = 1000) // Poll every 5 seconds
  @Transactional
  public void pollAndExecuteReadyJobs() {
    try {

      boolean leader = leaderLock.acquire("scheduler_leader_lock", 0);
      if (!leader) return;

      LocalDateTime now = LocalDateTime.now();
      List<ScheduledJob> readyJobs = scheduledJobRepository.findReadyToExecute(now);

      for (ScheduledJob scheduledJob : readyJobs) {
        Optional<Job> jobOpt = jobsRepository.findById(scheduledJob.getJobId());
        if (jobOpt.isEmpty()) {
          // Immediately delete orphaned schedule to prevent bloat
          log.warn("Job {} not found, removing orphaned schedule", scheduledJob.getJobId());
          scheduledJobRepository.delete(scheduledJob);
          continue;
        }

        Job job = jobOpt.get();

        // Verify job is still active
        if (JobStatus.ACTIVE != job.getStatus()) {
          deactivateScheduledJob(job.getId());
          continue;
        }

        // Execute the job
        long scheduleVersion = scheduledJob.getScheduleVersion();
        executeJobAsync(job);

        // Schedule next execution
        try {
          Cron cron = PARSER.parse(job.getSchedule());
          ExecutionTime executionTime = ExecutionTime.forCron(cron);
          scheduleNextExecution(job, executionTime, scheduleVersion);
        } catch (Exception e) {
          log.error(
              "Error scheduling next execution for job {}: {}",
              job.getId(),
              e.getMessage(),
              e);
        }
      }

      // Record metrics for scheduler poll
      metricsClient.recordSchedulerPoll(readyJobs.size());
      
      if (!readyJobs.isEmpty()) {
        log.debug("Executed {} ready jobs from database poll", readyJobs.size());
      }
    } catch (Exception e) {
      log.error("Error in pollAndExecuteReadyJobs: {}", e.getMessage(), e);
    }
  }

  /**
   * Periodically scans for jobs that need to be scheduled (for jobs that might have been missed
   * due to restarts).
   */
  @Scheduled(fixedDelay = 60000) // Run every minute
  public void scanAndScheduleJobs() {

    boolean leader = leaderLock.acquire("scheduler_leader_lock", 0);
    if (!leader) return;


    try {
      List<Job> activeJobs = new java.util.ArrayList<>();
      for (Job job : jobsRepository.findAll()) {
        if (JobStatus.ACTIVE == job.getStatus()
            && job.getSchedule() != null
            && !job.getSchedule().isEmpty()) {
          activeJobs.add(job);
        }
      }

      for (Job job : activeJobs) {
        // Check if already scheduled in database
        Optional<ScheduledJob> existing = scheduledJobRepository.findByJobId(job.getId());
        if (existing.isEmpty()
            || !existing.get().getIsActive()
            || existing.get().getNextRunTime().isBefore(LocalDateTime.now().minusHours(1))) {
          // Not scheduled or stale, reschedule
          scheduleJob(job);
        }
      }
      
      // Update metrics
      int scheduledCount = (int) scheduledJobRepository.count();
      metricsClient.updateScheduledJobsGauge(scheduledCount);
      metricsClient.updateActiveJobsGauge(activeJobs.size());
    } catch (Exception e) {
      log.error("Error in scanAndScheduleJobs: {}", e.getMessage(), e);
    }
  }

  /** Load and schedule all active jobs on startup */
  public void initializeScheduledJobs() {
    log.info("Initializing scheduled jobs...");

    boolean leader = leaderLock.acquire("scheduler_leader_lock", 0);
    if (!leader) return;

    // Clean up any orphaned or stale schedules first
    cleanupScheduledJobs();
    
    List<Job> activeJobs = new java.util.ArrayList<>();
    for (Job job : jobsRepository.findByStatus(JobStatus.ACTIVE)) {
      if (job.getSchedule() != null
          && !job.getSchedule().isEmpty()) {
        activeJobs.add(job);
      }
    }

    for (Job job : activeJobs) {
      scheduleJob(job);
    }
    log.info("Initialized {} scheduled jobs", activeJobs.size());
  }

  /**
   * Cleanup scheduled jobs table to prevent bloat. Removes:
   * 1. Inactive scheduled jobs older than 1 hour
   * 2. Stale schedules (not updated in 24 hours and past execution time)
   * 3. Orphaned schedules (for jobs that no longer exist)
   */
  @Scheduled(fixedDelay = 3600000) // Run every hour
  @Transactional
  public void cleanupScheduledJobs() {

    boolean leader = leaderLock.acquire("cleanup_leader_lock", 0);

    if (!leader) return;
    try {
      LocalDateTime now = LocalDateTime.now();
      
      // 1. Delete inactive schedules older than 1 hour
      LocalDateTime inactiveCutoff = now.minusHours(1);
      int deletedInactive = scheduledJobRepository.deleteInactiveSchedules(inactiveCutoff);
      if (deletedInactive > 0) {
        log.info("Cleaned up {} inactive scheduled jobs", deletedInactive);
      }

      // 2. Delete stale schedules (not updated in 24 hours and past execution time)
      LocalDateTime staleCutoff = now.minusHours(24);
      int deletedStale = scheduledJobRepository.deleteStaleSchedules(staleCutoff);
      if (deletedStale > 0) {
        log.info("Cleaned up {} stale scheduled jobs", deletedStale);
      }

      // 3. Delete orphaned schedules (for jobs that no longer exist)
      List<ScheduledJob> orphaned = scheduledJobRepository.findOrphanedSchedules();
      if (!orphaned.isEmpty()) {
        for (ScheduledJob orphan : orphaned) {
          scheduledJobRepository.delete(orphan);
        }
        log.info("Cleaned up {} orphaned scheduled jobs", orphaned.size());
      }

    } catch (Exception e) {
      log.error("Error cleaning up scheduled jobs: {}", e.getMessage(), e);
    }
  }
}
