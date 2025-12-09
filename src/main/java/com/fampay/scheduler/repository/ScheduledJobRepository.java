package com.fampay.scheduler.repository;

import com.fampay.scheduler.entity.ScheduledJob;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface ScheduledJobRepository extends JpaRepository<ScheduledJob, Long> {
  Optional<ScheduledJob> findByJobId(Long jobId);

  @Transactional
  @Modifying
  @Query("DELETE FROM ScheduledJob sj WHERE sj.jobId = :jobId")
  void deleteByJobId(Long jobId);

  @Query(
      "SELECT sj FROM ScheduledJob sj WHERE sj.isActive = true AND sj.nextRunTime <= :currentTime ORDER BY sj.nextRunTime ASC")
  List<ScheduledJob> findReadyToExecute(@Param("currentTime") LocalDateTime currentTime);

  @Query(
      "SELECT sj FROM ScheduledJob sj WHERE sj.jobId = :jobId AND sj.isActive = true AND sj.scheduleVersion = :version")
  Optional<ScheduledJob> findByJobIdAndVersion(
      @Param("jobId") Long jobId, @Param("version") Long version);

  @Transactional
  @Modifying
  @Query(
      "DELETE FROM ScheduledJob sj WHERE sj.isActive = false AND sj.updatedAt < :cutoffTime")
  int deleteInactiveSchedules(@Param("cutoffTime") LocalDateTime cutoffTime);

  @Transactional
  @Modifying
  @Query(
      "DELETE FROM ScheduledJob sj WHERE sj.updatedAt < :cutoffTime AND sj.nextRunTime < :cutoffTime")
  int deleteStaleSchedules(@Param("cutoffTime") LocalDateTime cutoffTime);

  @Query(
      "SELECT sj FROM ScheduledJob sj LEFT JOIN Job j ON sj.jobId = j.id WHERE j.id IS NULL")
  List<ScheduledJob> findOrphanedSchedules();
}

