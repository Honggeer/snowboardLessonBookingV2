package com.geer.snowboard.v2.scheduling.adapter.out.memory;

import com.geer.snowboard.v2.scheduling.application.port.out.TimeWindowPreviewStore;
import com.geer.snowboard.v2.scheduling.domain.TimeWindow;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

/** Ephemeral storage for a local demonstration; previews disappear on restart. */
@Repository
@Profile("local")
public class InMemoryTimeWindowPreviewStore implements TimeWindowPreviewStore {

    private final Map<String, TimeWindow> windows = new ConcurrentHashMap<>();

    @Override
    public String save(TimeWindow window) {
        String id = UUID.randomUUID().toString();
        windows.put(id, window);
        return id;
    }

    @Override
    public Optional<TimeWindow> find(String id) {
        return Optional.ofNullable(windows.get(id));
    }

    public int size() {
        return windows.size();
    }
}
