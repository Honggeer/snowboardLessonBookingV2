package com.geer.snowboard.v2.scheduling.domain;

import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

public final class AvailabilityRange {
    public static final ZoneId ZONE = ZoneId.of("America/Toronto");
    public record Window(Instant start, Instant end) {}
    public record Split(List<Window> windows, LocalTime tailStart, LocalTime tailEnd) {}

    private AvailabilityRange() {}

    public static Split split(LocalDate date, LocalTime start, LocalTime end) {
        if (start == null || end == null || !end.isAfter(start)
                || start.getSecond() != 0 || start.getNano() != 0
                || end.getSecond() != 0 || end.getNano() != 0)
            throw new IllegalArgumentException("请输入同一天内有效的整分钟时间范围");
        List<Window> windows = new ArrayList<>();
        LocalTime cursor = start;
        while (!cursor.plusHours(2).isAfter(end) && !cursor.plusHours(2).isBefore(cursor)) {
            LocalTime next = cursor.plusHours(2);
            Instant begin = uniqueInstant(date, cursor);
            Instant finish = uniqueInstant(date, next);
            if (!Duration.between(begin, finish).equals(Duration.ofHours(2)))
                throw new IllegalArgumentException("夏令时切换范围不能拆成完整两小时");
            windows.add(new Window(begin, finish));
            cursor = next;
        }
        if (windows.isEmpty()) throw new IllegalArgumentException("每天至少需要完整两小时");
        return new Split(List.copyOf(windows), cursor.isBefore(end) ? cursor : null,
                cursor.isBefore(end) ? end : null);
    }

    private static Instant uniqueInstant(LocalDate date, LocalTime time) {
        LocalDateTime local = LocalDateTime.of(date, time);
        var offsets = ZONE.getRules().getValidOffsets(local);
        if (offsets.size() != 1) throw new DateTimeException("当地时间不存在或重复");
        return local.toInstant(offsets.getFirst());
    }
}
