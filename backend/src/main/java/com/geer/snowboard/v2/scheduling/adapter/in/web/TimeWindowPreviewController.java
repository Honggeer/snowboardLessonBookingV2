package com.geer.snowboard.v2.scheduling.adapter.in.web;

import com.geer.snowboard.v2.scheduling.application.port.in.CreateTimeWindowPreview;
import com.geer.snowboard.v2.scheduling.application.port.in.FindTimeWindowPreview;
import com.geer.snowboard.v2.scheduling.application.port.in.TimeWindowPreview;
import java.net.URI;
import java.time.Instant;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Local-only demonstration API; it never creates real availability or bookings. */
@RestController
@Profile("local")
@RequestMapping("/api/demo/time-window-previews")
public final class TimeWindowPreviewController {

    private final CreateTimeWindowPreview createPreview;
    private final FindTimeWindowPreview findPreview;

    public TimeWindowPreviewController(CreateTimeWindowPreview createPreview,
                                       FindTimeWindowPreview findPreview) {
        this.createPreview = createPreview;
        this.findPreview = findPreview;
    }

    @GetMapping("/csrf")
    public CsrfToken csrf(CsrfToken token) {
        return token;
    }

    @PostMapping
    public ResponseEntity<PreviewResponse> create(@RequestBody PreviewRequest request) {
        try {
            TimeWindowPreview preview = createPreview.create(
                    new CreateTimeWindowPreview.Command(request.start(), request.end()));
            URI location = URI.create("/api/demo/time-window-previews/" + preview.id());
            return ResponseEntity.created(location).body(PreviewResponse.from(preview));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    @GetMapping("/{id}")
    public PreviewResponse find(@PathVariable String id) {
        return findPreview.find(id)
                .map(PreviewResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    public record PreviewRequest(Instant start, Instant end) {}

    public record PreviewResponse(String id, Instant start, Instant end, long durationSeconds) {
        static PreviewResponse from(TimeWindowPreview preview) {
            return new PreviewResponse(preview.id(), preview.start(), preview.end(),
                    preview.durationSeconds());
        }
    }
}
