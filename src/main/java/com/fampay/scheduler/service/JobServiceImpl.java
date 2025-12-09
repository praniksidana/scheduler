package com.fampay.scheduler.service;

import com.fampay.scheduler.entity.Job;
import com.fampay.scheduler.enums.JobStatus;
import com.fampay.scheduler.exception.InternalErrorException;
import com.fampay.scheduler.exception.InvalidReqParameters;
import com.fampay.scheduler.exception.JobNotFoundException;
import com.fampay.scheduler.models.JobExecution;
import com.fampay.scheduler.models.JobSpec;
import com.fampay.scheduler.repository.JobExecutionRepository;
import com.fampay.scheduler.repository.JobsRepository;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class JobServiceImpl implements JobService {

  private final JobsRepository jobsRepo;
  private final JobExecutionRepository jobExecutionRepo;
  private final JobSchedulerService schedulerService;

  @Autowired
  public JobServiceImpl(
      JobsRepository jobsRepo,
      JobExecutionRepository jobExecutionRepo,
      JobSchedulerService schedulerService) {
    this.jobsRepo = jobsRepo;
    this.jobExecutionRepo = jobExecutionRepo;
    this.schedulerService = schedulerService;
  }

  @Override
  @Transactional
  public Long createJobFromSpec(JobSpec jobSpec) {
    log.info(
        "Creating job from spec: schedule={}, api={}, type={}",
        jobSpec.getSchedule(),
        jobSpec.getApi(),
        jobSpec.getType());

    // Validate input
    if (jobSpec.getSchedule() == null || jobSpec.getSchedule().trim().isEmpty()) {
      throw new InvalidReqParameters("schedule cannot be null or empty");
    }
    if (jobSpec.getApi() == null || jobSpec.getApi().trim().isEmpty()) {
      throw new InvalidReqParameters("api cannot be null or empty");
    }
    if (jobSpec.getType() == null) {
      throw new InvalidReqParameters("type cannot be null");
    }

    // Validate CRON expression
    schedulerService.validateCronExpression(jobSpec.getSchedule());

    // Create job entity
    Job job =
        Job.builder()
            .schedule(jobSpec.getSchedule())
            .apiEndpoint(jobSpec.getApi())
            .type(jobSpec.getType().name())
            .status(JobStatus.ACTIVE)
            .name("job-" + System.currentTimeMillis()) // Auto-generate name
            .build();

    try {
      Job savedJob = jobsRepo.save(job);
      log.info("Job created with ID: {}", savedJob.getId());

      // Schedule the job
      schedulerService.scheduleJob(savedJob);

      return savedJob.getId();
    } catch (Exception e) {
      log.error("Error creating job from spec: {}", e.getMessage(), e);
      throw new InternalErrorException("Failed to create job: " + e.getMessage());
    }
  }

  @Override
  public List<JobExecution> getExecutions(String jobId, Pageable pageable) {
    log.info("Getting last {} executions for job ID: {}", jobId, pageable);

    try {
      Long jobIdLong = Long.parseLong(jobId);
      Optional<Job> jobOpt = jobsRepo.findById(jobIdLong);
      if (jobOpt.isEmpty()) {
        throw new JobNotFoundException("Job not found with ID: " + jobId);
      }

      List<com.fampay.scheduler.entity.JobExecution> executions =
          jobExecutionRepo.findByJobIdOrderByRunAtDesc(jobIdLong, pageable);

      return executions.stream()
          .map(
              e ->
                  JobExecution.builder()
                      .timestamp(e.getRunAt())
                      .httpStatus(e.getHttpStatus())
                      .timeTakenMs(e.getTimeTakenMs())
                      .error(e.getError())
                      .build())
          .toList();
    } catch (NumberFormatException e) {
      throw new InvalidReqParameters("Invalid job ID format: " + jobId);
    }
  }
}
