package com.example.birdlensapi.domain.tour.dto;

import com.example.birdlensapi.domain.tour.Tour;

import java.math.BigDecimal;
import java.util.UUID;

public record TourResponse(
        UUID id,
        String name,
        String description,
        BigDecimal price,
        Integer capacity,
        String thumbnailUrl,
        Integer durationHours,
        UUID eventId
) {
    public static TourResponse fromEntity(Tour tour) {
        return new TourResponse(
                tour.getId(),
                tour.getName(),
                tour.getDescription(),
                tour.getPrice(),
                tour.getCapacity(),
                tour.getThumbnailUrl(),
                tour.getDurationHours(),
                tour.getEvent() != null ? tour.getEvent().getId() : null
        );
    }
}