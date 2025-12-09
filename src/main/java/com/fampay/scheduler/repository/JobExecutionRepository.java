package com.fampay.scheduler.repository;

import com.fampay.scheduler.entity.JobExecution;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JobExecutionRepository extends JpaRepository<JobExecution, Long> {
  List<JobExecution> findByJobIdOrderByRunAtDesc(Long jobId, Pageable pageable);

  @Query("SELECT je FROM JobExecution je WHERE je.jobId = :jobId ORDER BY je.runAt DESC")
  List<JobExecution> findLastExecutionsByJobId(@Param("jobId") Long jobId, Pageable pageable);
}
