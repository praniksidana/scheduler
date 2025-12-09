package com.fampay.scheduler.clients;

import com.fampay.scheduler.models.TaggedCounter;

public interface MetricsClient {
  void incReqCount(TaggedCounter taggedCtr);

  void incFailedReqCount(TaggedCounter taggedCtr);

  void recordJobExecution(Long jobId, String jobType, Integer httpStatus, Long durationMs);

  void recordJobExecutionFailure(Long jobId, String jobType, String error);

  void recordSchedulerPoll(int jobsProcessed);

  void updateScheduledJobsGauge(int count);

  void updateActiveJobsGauge(int count);
}
