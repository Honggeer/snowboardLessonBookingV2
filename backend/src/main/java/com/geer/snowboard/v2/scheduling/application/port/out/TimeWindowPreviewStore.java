package com.geer.snowboard.v2.scheduling.application.port.out;

import com.geer.snowboard.v2.scheduling.domain.TimeWindow;
import java.util.Optional;

public interface TimeWindowPreviewStore {

    String save(TimeWindow window);

    Optional<TimeWindow> find(String id);
}
