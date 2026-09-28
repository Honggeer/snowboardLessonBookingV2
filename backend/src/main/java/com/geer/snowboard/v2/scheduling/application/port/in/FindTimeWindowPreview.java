package com.geer.snowboard.v2.scheduling.application.port.in;

import java.util.Optional;

public interface FindTimeWindowPreview {

    Optional<TimeWindowPreview> find(String id);
}
