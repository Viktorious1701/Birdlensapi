package com.example.birdlensapi.domain.tour.dto;

import java.util.List;

public record EventPageResponse(
        List<EventResponse> content,
        int pageNumber,
        int pageSize,
        long totalElements,
        int totalPages,
        boolean isLast
) {}