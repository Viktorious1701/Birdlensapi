package com.example.birdlensapi.domain.tour.dto;

import java.util.List;

public record TourPageResponse(
        List<TourResponse> content,
        int pageNumber,
        int pageSize,
        long totalElements,
        int totalPages,
        boolean isLast
) {}