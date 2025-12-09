package com.fampay.scheduler.exception;

public class JobAlreadyExistsException extends RuntimeException {
  public JobAlreadyExistsException(String s) {
    super(s);
  }
}
