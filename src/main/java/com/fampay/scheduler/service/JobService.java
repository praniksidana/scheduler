package com.fampay.scheduler.service;

import com.fampay.scheduler.models.JobExecution;
import com.fampay.scheduler.models.JobSpec;
import java.util.List;
import org.springframework.data.domain.Pageable;

public interface JobService {
  /**
   * Create a job from JobSpec and return the job ID.
   *
   * @param jobSpec Job specification with schedule, API endpoint, and type
   * @return Job ID
   */
  Long createJobFromSpec(JobSpec jobSpec);

  /**
   * Get last N executions for a given job ID.
   *
   * @param jobId Job ID
   * @param pageable Pageable
   * @return List of last N executions with timestamp, HTTP status, and time taken
   */
  List<JobExecution> getExecutions(String jobId, Pageable pageable);
}
