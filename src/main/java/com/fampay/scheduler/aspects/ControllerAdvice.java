package com.fampay.scheduler.aspects;

import com.fampay.scheduler.exception.*;
import com.fampay.scheduler.models.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ControllerAdvice {

  @ExceptionHandler({InvalidReqParameters.class})
  public ResponseEntity<?> onInvalidReqParamsException(Exception e) {
    ErrorResponse res = ErrorResponse.builder().success(false).message(e.getMessage()).build();
    res.setTitle(e.getMessage());
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(res);
  }

  @ExceptionHandler({JobAlreadyExistsException.class})
  public ResponseEntity<?> onJobAlreadyExistsException(Exception e) {
    ErrorResponse res = ErrorResponse.builder().success(false).message(e.getMessage()).build();
    res.setTitle(e.getMessage());
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(res);
  }

  @ExceptionHandler({JobNotFoundException.class})
  public ResponseEntity<?> onJobNotFoundException(Exception e) {
    ErrorResponse res = ErrorResponse.builder().success(false).message(e.getMessage()).build();
    res.setTitle(e.getMessage());
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(res);
  }

  @ExceptionHandler(InternalErrorException.class)
  public ResponseEntity<?> onInternalError(Exception e) {
    ErrorResponse res = ErrorResponse.builder().success(false).message(e.getMessage()).build();
    res.setTitle(e.getMessage());
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(res);
  }
}
