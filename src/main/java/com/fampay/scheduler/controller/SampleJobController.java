package com.fampay.scheduler.controller;


import com.fampay.scheduler.models.CreateJobResponse;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/sample-run")
public class SampleJobController {

  private final ExecutorService sampleJobExecutor;

  public SampleJobController(@Qualifier("sampleJobExecutor") ExecutorService sampleJobExecutor) {
    this.sampleJobExecutor = sampleJobExecutor;
  }

  @PostMapping
  public ResponseEntity<CreateJobResponse> runJob() {
    log.info("sampleRunJob");
    Future<?> future = sampleJobExecutor.submit(() -> {
      double min = 10;
      double max = 90000;
      double lambda = 0.005;
      double sleepMs = min + (-Math.log(1 - Math.random()) / lambda);
      sleepMs = Math.min(sleepMs, max);
      try {
        Thread.sleep((long) sleepMs);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    });

    try {
      future.get(); // Wait for completion
    } catch (Exception e) {
      log.error("Job execution interrupted", e);
      // Optionally handle error in response
    }

    // Optionally, you can wait for completion or return immediately
    CreateJobResponse response = new CreateJobResponse();
    return ResponseEntity.ok(response);
  }
}

