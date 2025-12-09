package com.fampay.scheduler.exception;

public class JobNotFoundException extends RuntimeException {
  public JobNotFoundException(String s) {
    super(s);
  }
}
