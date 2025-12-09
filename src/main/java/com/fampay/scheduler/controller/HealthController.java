package com.fampay.scheduler.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
public class HealthController {
  @GetMapping(path = "/")
  public ResponseEntity getHealthCheck() {
    return ResponseEntity.ok().build();
  }
}
