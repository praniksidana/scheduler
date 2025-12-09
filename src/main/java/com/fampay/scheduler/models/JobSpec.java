package com.fampay.scheduler.models;

import com.fampay.scheduler.enums.JobType;
import lombok.Data;

@Data
public class JobSpec {
  private String schedule; // 6-field CRON: "second minute hour day month dayOfWeek"
  private String api; // HTTP endpoint URL
  private JobType type; // ATLEAST_ONCE or ATMOST_ONCE
}
