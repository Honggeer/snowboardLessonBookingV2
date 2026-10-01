package com.geer.snowboard.v2.scheduling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.geer.snowboard.v2.scheduling.domain.AvailabilityRange;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;

class AvailabilityRangeTest {
    @Test
    void splitsOnlyCompleteRealTwoHourWindowsAndReportsTheTail() {
        var split = AvailabilityRange.split(LocalDate.of(2026, 10, 10),
                LocalTime.of(9, 0), LocalTime.of(14, 30));
        assertThat(split.windows()).hasSize(2);
        assertThat(split.tailStart()).isEqualTo(LocalTime.of(13, 0));
        assertThat(split.tailEnd()).isEqualTo(LocalTime.of(14, 30));
        assertThat(split.windows().getFirst().end()).isEqualTo(split.windows().get(1).start());
    }

    @Test
    void rejectsRangesThatCrossTorontoDaylightSavingTransitions() {
        var transition = AvailabilityRange.ZONE.getRules().nextTransition(Instant.now());
        while (!transition.isGap()) transition = AvailabilityRange.ZONE.getRules()
                .nextTransition(transition.getInstant());
        LocalDate spring = transition.getDateTimeBefore().toLocalDate();
        assertThatThrownBy(() -> AvailabilityRange.split(spring, LocalTime.of(1, 0), LocalTime.of(5, 0)))
                .isInstanceOf(IllegalArgumentException.class);
        while (!transition.isOverlap()) transition = AvailabilityRange.ZONE.getRules()
                .nextTransition(transition.getInstant());
        LocalDate autumn = transition.getDateTimeBefore().toLocalDate();
        assertThatThrownBy(() -> AvailabilityRange.split(autumn, LocalTime.of(0, 30), LocalTime.of(4, 30)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
