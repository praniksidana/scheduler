package com.fampay.scheduler.models;

import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class ErrorResponse {
  private boolean success;
  private String message;
  private Object data;
  private String title;
}
