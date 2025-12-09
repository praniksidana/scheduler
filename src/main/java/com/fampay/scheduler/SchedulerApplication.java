package com.fampay.scheduler;

import com.fampay.scheduler.service.JobSchedulerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@Slf4j
@SpringBootApplication
public class SchedulerApplication implements CommandLineRunner {

  @Autowired private JobSchedulerService schedulerService;

  public static void main(String[] args) {
    SpringApplication.run(SchedulerApplication.class, args);
  }

  @Override
  public void run(String... args) {
    log.info("Starting Job Scheduler Application...");
    // Initialize and schedule all active jobs on startup

    schedulerService.initializeScheduledJobs();
    log.info("Job Scheduler Application started successfully");
  }
}
