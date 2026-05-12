package com.example.birdlensapi.domain.tour.dto;

import com.example.birdlensapi.domain.tour.Event;

import java.time.Instant;
import java.util.UUID;

public record EventResponse(
        UUID id,
        String title,
        String description,
        String coverPhotoUrl,
        Instant startDate,
        Instant endDate
) {
    public static EventResponse fromEntity(Event event) {
        return new EventResponse(
                event.getId(),
                event.getTitle(),
                event.getDescription(),
                event.getCoverPhotoUrl(),
                event.getStartDate(),
                event.getEndDate()
        );
    }
}