package com.fampay.scheduler.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UpdateTimestamp;

@Data
@Builder
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(
    name = "scheduled_jobs",
    indexes = {
      @Index(name = "idx_scheduled_jobs_job_id", columnList = "job_id"),
      @Index(name = "idx_scheduled_jobs_next_run_time", columnList = "next_run_time"),
      @Index(name = "idx_scheduled_jobs_status", columnList = "job_id,is_active")
    })
public class ScheduledJob {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column
  private Long id;

  @Column(name = "job_id", nullable = false, unique = true)
  private Long jobId;

  @Column(name = "next_run_time", nullable = false)
  private LocalDateTime nextRunTime;

  @Column(name = "is_active", nullable = false)
  private Boolean isActive;

  @Column(name = "schedule_version")
  private Long scheduleVersion; // Incremented when schedule changes to invalidate old entries

  @Column(name = "updated_at")
  @UpdateTimestamp
  private LocalDateTime updatedAt;
}

