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
import org.hibernate.annotations.CreationTimestamp;

@Data
@Builder
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(
    name = "job_executions",
    indexes = {
      @Index(name = "idx_job_id_run_at", columnList = "job_id,run_at"),
      @Index(name = "idx_job_id", columnList = "job_id")
    })
public class JobExecution {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column
  private Long id;

  @Column(name = "job_id", nullable = false)
  private Long jobId;

  @Column(name = "run_at", nullable = false)
  private LocalDateTime runAt;

  @Column(name = "http_status")
  private Integer httpStatus;

  @Column(name = "time_taken_ms")
  private Long timeTakenMs;

  @Column(name = "error", length = 2048)
  private String error;

  @Column(name = "created_at")
  @CreationTimestamp
  private LocalDateTime createdAt;
}
