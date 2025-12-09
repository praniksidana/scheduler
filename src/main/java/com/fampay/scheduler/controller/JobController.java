package com.fampay.scheduler.controller;

import static com.fampay.scheduler.constants.MicrometerConstants.CREATE_JOB;
import static com.fampay.scheduler.constants.MicrometerConstants.GET_EXECUTIONS;

import com.fampay.scheduler.clients.MetricsClient;
import com.fampay.scheduler.exception.InvalidReqParameters;
import com.fampay.scheduler.lib.HTTPResponder;
import com.fampay.scheduler.models.CreateJobResponse;
import com.fampay.scheduler.models.JobExecution;
import com.fampay.scheduler.models.JobSpec;
import com.fampay.scheduler.models.TaggedCounter;
import com.fampay.scheduler.service.JobService;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.SortDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@EnableScheduling
@RequestMapping("/v1/schedules")
public class JobController {

  private final JobService jobService;
  private final MetricsClient metricsClient;

  @Autowired
  public JobController(JobService jobService, MetricsClient metricsClient) {
    this.jobService = jobService;
    this.metricsClient = metricsClient;
  }

  /**
   * Create a job using the Job Spec format.
   *
   * @param jobSpec Job specification with schedule (CRON), API endpoint, and type
   * @return Job ID
   */
  @PostMapping(path = "/jobs")
  public ResponseEntity<CreateJobResponse> createJob(@RequestBody JobSpec jobSpec) {
    log.info("Creating job from spec: schedule={}, api={}, type={}", 
        jobSpec.getSchedule(), jobSpec.getApi(), jobSpec.getType());
    
    metricsClient.incReqCount(TaggedCounter.builder().callType(CREATE_JOB).build());
    
    if (jobSpec.getSchedule() == null || jobSpec.getSchedule().trim().isEmpty()) {
      throw new InvalidReqParameters("schedule cannot be null or empty");
    }
    if (jobSpec.getApi() == null || jobSpec.getApi().trim().isEmpty()) {
      throw new InvalidReqParameters("api cannot be null or empty");
    }
    if (jobSpec.getType() == null) {
      throw new InvalidReqParameters("type cannot be null");
    }

    try {
      Long jobId = jobService.createJobFromSpec(jobSpec);
      CreateJobResponse response = CreateJobResponse.builder().jobId(jobId).build();
      return HTTPResponder.created(response);
    } catch (Exception e) {
      log.error("Error creating job from spec: {}", e.getMessage(), e);
      metricsClient.incFailedReqCount(
          TaggedCounter.builder().callType(CREATE_JOB).exceptionType(e).build());
      throw e;
    }
  }

  /**
   * Get last N executions for a given job ID.
   *
   * @param jobId Job ID
   * @param size Number of executions to return (default: 10, max: 100)
   * @return List of last N executions with timestamp, HTTP status, and time taken
   */
  @GetMapping(path = "/jobs/{jobId}/executions")
  public ResponseEntity<List<JobExecution>> getExecutions(
      @PathVariable String jobId,
      @PageableDefault(page = 0, size = 20)
      @SortDefault.SortDefaults({@SortDefault(sort = "id", direction = Direction.DESC)})
      Pageable pageable) {
    if (Strings.isEmpty(jobId)) {
      throw new InvalidReqParameters("jobId can't be null");
    }

    metricsClient.incReqCount(TaggedCounter.builder().callType(GET_EXECUTIONS).build());
    
    log.info("Getting last {} executions for job ID: {}", jobId, pageable);
    try {
      List<JobExecution> executions = jobService.getExecutions(jobId, pageable);
      return HTTPResponder.success(executions);
    } catch (Exception e) {
      log.error("Error getting executions for job {}: {}", jobId, e.getMessage(), e);
      metricsClient.incFailedReqCount(
          TaggedCounter.builder().callType(GET_EXECUTIONS).exceptionType(e).build());
      throw e;
    }
  }
}
