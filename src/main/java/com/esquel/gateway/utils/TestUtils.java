package com.esquel.gateway.utils;

import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;

public class TestUtils {

  public static void testTakeTime(String testName, Supplier<Instant> supplier) {

    Instant start = Instant.now();

    System.err.println(String.format(":::Start test [%s] = [%s]毫秒::::", testName, start.toEpochMilli()));

    Instant end = supplier.get();

    System.err.println(String.format(":::END test [%s] = [%s],耗时 = [%s]毫秒::::%n", testName, end.toEpochMilli(), Duration.between(start, end).toMillis()));

  }
}
