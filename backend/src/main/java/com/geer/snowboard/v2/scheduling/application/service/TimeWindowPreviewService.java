package com.geer.snowboard.v2.scheduling.application.service;

import com.geer.snowboard.v2.scheduling.application.port.in.CreateTimeWindowPreview;
import com.geer.snowboard.v2.scheduling.application.port.in.FindTimeWindowPreview;
import com.geer.snowboard.v2.scheduling.application.port.in.TimeWindowPreview;
import com.geer.snowboard.v2.scheduling.application.port.out.TimeWindowPreviewStore;
import com.geer.snowboard.v2.scheduling.domain.TimeWindow;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("local")
public final class TimeWindowPreviewService
        implements CreateTimeWindowPreview, FindTimeWindowPreview {

    private final TimeWindowPreviewStore store;

    public TimeWindowPreviewService(TimeWindowPreviewStore store) {
        this.store = store;
    }

    @Override
    public TimeWindowPreview create(Command command) {
        TimeWindow window = TimeWindow.of(command.start(), command.end());
        String id = store.save(window);
        return toResult(id, window);
    }

    @Override
    public Optional<TimeWindowPreview> find(String id) {
        return store.find(id).map(window -> toResult(id, window));
    }

    private static TimeWindowPreview toResult(String id, TimeWindow window) {
        return new TimeWindowPreview(
                id, window.start(), window.end(), window.duration().toSeconds());
    }
}
