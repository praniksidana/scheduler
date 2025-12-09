package com.fampay.scheduler.constants;

import java.time.ZoneOffset;

public class JobConstants {
  private JobConstants() {}

  public static final String ACTIVE = "active";
  public static final String PAUSE = "pause";
  public static final String HEADERS = "headers";
  public static final ZoneOffset TIME_ZONE_OFFSET = ZoneOffset.of("+05:30");
}
