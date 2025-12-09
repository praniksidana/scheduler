package com.fampay.scheduler.lib;

import java.util.HashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

public class HTTPResponder {
  public static ResponseEntity created(Object data) {
    Map<String, Object> res =
        new HashMap<>() {
          {
            put("success", true);
            put("data", data);
          }
        };
    return new ResponseEntity<>(res, HttpStatus.CREATED);
  }

  public static ResponseEntity failed(Object data, String message) {
    Map<String, Object> res =
        new HashMap<>() {
          {
            put("success", false);
            put("message", message);
            put("data", data);
          }
        };
    return new ResponseEntity<>(res, HttpStatus.INTERNAL_SERVER_ERROR);
  }

  public static ResponseEntity success(Object data) {
    Map<String, Object> res =
        new HashMap<>() {
          {
            put("success", true);
            put("data", data);
          }
        };

    return new ResponseEntity<>(res, HttpStatus.OK);
  }

  public static ResponseEntity deleted() {
    return new ResponseEntity<>(HttpStatus.NO_CONTENT);
  }

  public static ResponseEntity multiStatus(Object data) {
    Map<String, Object> res =
        new HashMap<>() {
          {
            put("success", true);
            put("data", data);
          }
        };
    return new ResponseEntity<>(res, HttpStatus.MULTI_STATUS);
  }
}
