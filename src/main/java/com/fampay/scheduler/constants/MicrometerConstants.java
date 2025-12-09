package com.fampay.scheduler.constants;

public class MicrometerConstants {
  private MicrometerConstants() {}

  public static final String EXCEPTION_TYPE = "EXCEPTION_TYPE";
  public static final String CALL_TYPE = "CALL_TYPE";

  public static final String METRIC_PREFIX = "schedulersvc_";
  public static final String METRIC_SUFFIX = "_total";
  public static final String REQUESTS_TOTAL = METRIC_PREFIX + "requests" + METRIC_SUFFIX;
  public static final String REQUESTS_FAILED_TOTAL =
      METRIC_PREFIX + "requests_failed" + METRIC_SUFFIX;

  public static final String ZOMBIE_JOBS = METRIC_PREFIX + "zombie_jobs" + METRIC_SUFFIX;
  public static final String CREATE_JOB = "CREATE_JOB";
  public static final String CREATE_JOB_SCHEDULE = "CREATE_JOB_SCHEDULE";
  public static final String GET_EXECUTIONS = "GET_EXECUTIONS";
  
  // New metrics for job scheduler
  public static final String JOB_EXECUTIONS_TOTAL = METRIC_PREFIX + "job_executions" + METRIC_SUFFIX;
  public static final String JOB_EXECUTIONS_SUCCESS = METRIC_PREFIX + "job_executions_success" + METRIC_SUFFIX;
  public static final String JOB_EXECUTIONS_FAILED = METRIC_PREFIX + "job_executions_failed" + METRIC_SUFFIX;
  public static final String JOB_EXECUTION_DURATION = METRIC_PREFIX + "job_execution_duration_ms";
  public static final String SCHEDULED_JOBS_COUNT = METRIC_PREFIX + "scheduled_jobs_count";
  public static final String ACTIVE_JOBS_COUNT = METRIC_PREFIX + "active_jobs_count";
  public static final String SCHEDULER_POLLS_TOTAL = METRIC_PREFIX + "scheduler_polls" + METRIC_SUFFIX;
  
  // Tags
  public static final String JOB_ID = "job_id";
  public static final String JOB_TYPE = "job_type";
  public static final String HTTP_STATUS = "http_status";
  public static final String STATUS = "status";
}
