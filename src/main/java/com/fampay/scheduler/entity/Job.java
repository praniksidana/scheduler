package com.fampay.scheduler.entity;

import com.fampay.scheduler.enums.JobStatus;
import com.fampay.scheduler.util.JsonToMapConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Data
@Builder
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "jobs")
public class Job {
  public static final Long serialVersionUID = -1L;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column
  private Long id;

  @Column private String name;

  @Column private String type; // JobType enum as string: ATLEAST_ONCE, ATMOST_ONCE

  @Column
  @Enumerated(EnumType.STRING)
  private JobStatus status; // active, paused

  @Column(name = "schedule", length = 100)
  private String schedule; // 6-field CRON expression

  @Column(name = "api_endpoint", length = 2048)
  private String apiEndpoint; // HTTP endpoint URL

  @Column(columnDefinition = "json")
  @Convert(converter = JsonToMapConverter.class)
  private Map payload;

  @Column(name = "created_at")
  @CreationTimestamp
  private LocalDateTime createdAt;

  @Column(name = "updated_at")
  @UpdateTimestamp
  private LocalDateTime updatedAt;
}
