package com.fampay.scheduler.clients;

import static com.fampay.scheduler.constants.MicrometerConstants.*;

import com.fampay.scheduler.models.TaggedCounter;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class MicrometerMetricsImpl implements MetricsClient {

  private final MeterRegistry meterRegistry;
  private final AtomicInteger scheduledJobsGauge = new AtomicInteger(0);
  private final AtomicInteger activeJobsGauge = new AtomicInteger(0);

  @Autowired
  public MicrometerMetricsImpl(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
    // Register gauges
    Gauge.builder(SCHEDULED_JOBS_COUNT, scheduledJobsGauge, AtomicInteger::get)
        .description("Number of scheduled jobs")
        .register(meterRegistry);
    Gauge.builder(ACTIVE_JOBS_COUNT, activeJobsGauge, AtomicInteger::get)
        .description("Number of active jobs")
        .register(meterRegistry);
  }

  // incReqCount: Keeps track of all requests received
  public void incReqCount(TaggedCounter taggedCtr) {
    String callType = taggedCtr.getCallType();
    log.info("{}_REQ_RECEIVED", callType);
    incCtr(REQUESTS_TOTAL, taggedCtr);
  }

  // incFailedReqCount: Keeps track of all failed requests
  public void incFailedReqCount(TaggedCounter taggedCtr) {
    String callType = taggedCtr.getCallType();
    log.info("{}_REQ_FAILED", callType);
    incFailCtr(REQUESTS_FAILED_TOTAL, taggedCtr);
  }

  // incCtr: Increments counter for the given tags with the value provided as taggedCtr
  private void incCtr(String metricName, TaggedCounter taggedCtr) {
    String callType = taggedCtr.getCallType();
    Counter ctr = Metrics.counter(metricName, CALL_TYPE, callType);
    ctr.increment(taggedCtr.getCount());
  }

  // incFailCtr: Increments counter for the failed request
  // given tags with the value provided as taggedCtr
  private void incFailCtr(String metricName, TaggedCounter taggedCtr) {
    String callType = taggedCtr.getCallType();
    String exceptionType = "NullException";
    Exception e = taggedCtr.getExceptionType();

    if (e != null) {
      exceptionType = e.getClass().getSimpleName();
    }

    Counter ctr = Metrics.counter(metricName, CALL_TYPE, callType, EXCEPTION_TYPE, exceptionType);
    ctr.increment();
  }

  @Override
  public void recordJobExecution(Long jobId, String jobType, Integer httpStatus, Long durationMs) {
    // Record total executions
    Counter.builder(JOB_EXECUTIONS_TOTAL)
        .tag(JOB_ID, String.valueOf(jobId))
        .tag(JOB_TYPE, jobType)
        .description("Total number of job executions")
        .register(meterRegistry)
        .increment();

    // Record success/failure based on HTTP status
    if (httpStatus != null && httpStatus >= 200 && httpStatus < 300) {
      Counter.builder(JOB_EXECUTIONS_SUCCESS)
          .tag(JOB_ID, String.valueOf(jobId))
          .tag(JOB_TYPE, jobType)
          .tag(HTTP_STATUS, String.valueOf(httpStatus))
          .description("Successful job executions")
          .register(meterRegistry)
          .increment();
    } else {
      Counter.builder(JOB_EXECUTIONS_FAILED)
          .tag(JOB_ID, String.valueOf(jobId))
          .tag(JOB_TYPE, jobType)
          .tag(HTTP_STATUS, httpStatus != null ? String.valueOf(httpStatus) : "unknown")
          .description("Failed job executions")
          .register(meterRegistry)
          .increment();
    }

    // Record execution duration as a timer
    if (durationMs != null) {
      Timer timer =
          Timer.builder(JOB_EXECUTION_DURATION)
              .tag(JOB_ID, String.valueOf(jobId))
              .tag(JOB_TYPE, jobType)
              .description("Job execution duration in milliseconds")
              .register(meterRegistry);
      timer.record(durationMs, java.util.concurrent.TimeUnit.MILLISECONDS);
    }
  }

  @Override
  public void recordJobExecutionFailure(Long jobId, String jobType, String error) {
    Counter.builder(JOB_EXECUTIONS_FAILED)
        .tag(JOB_ID, String.valueOf(jobId))
        .tag(JOB_TYPE, jobType)
        .tag(HTTP_STATUS, "error")
        .description("Failed job executions")
        .register(meterRegistry)
        .increment();
    
    // Also increment total to track all executions
    Counter.builder(JOB_EXECUTIONS_TOTAL)
        .tag(JOB_ID, String.valueOf(jobId))
        .tag(JOB_TYPE, jobType)
        .description("Total number of job executions")
        .register(meterRegistry)
        .increment();
  }

  @Override
  public void recordSchedulerPoll(int jobsProcessed) {
    Counter.builder(SCHEDULER_POLLS_TOTAL)
        .tag(STATUS, jobsProcessed > 0 ? "jobs_found" : "no_jobs")
        .description("Number of scheduler polls")
        .register(meterRegistry)
        .increment();
  }

  @Override
  public void updateScheduledJobsGauge(int count) {
    scheduledJobsGauge.set(count);
  }

  @Override
  public void updateActiveJobsGauge(int count) {
    activeJobsGauge.set(count);
  }
}
