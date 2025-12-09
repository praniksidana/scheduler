package com.fampay.scheduler.models;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobExecution {
  @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
  private LocalDateTime timestamp;
  
  private Integer httpStatus;
  
  private Long timeTakenMs;
  
  private String error; // Optional error message
}
