package com.fampay.scheduler.repository;

import com.fampay.scheduler.entity.Job;
import com.fampay.scheduler.enums.JobStatus;
import java.util.List;
import org.springframework.data.repository.CrudRepository;

public interface JobsRepository extends CrudRepository<Job, Long> {

  List<Job> findByStatus(JobStatus status);
}
