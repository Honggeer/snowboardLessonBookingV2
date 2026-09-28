package com.geer.snowboard.v2.scheduling.application.port.in;

import java.time.Instant;

public record TimeWindowPreview(String id, Instant start, Instant end, long durationSeconds) {}
