package com.fampay.scheduler.models;

import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class TaggedCounter {
  private String callType;
  private Exception exceptionType;
  @Builder.Default private int count = 1;
}
