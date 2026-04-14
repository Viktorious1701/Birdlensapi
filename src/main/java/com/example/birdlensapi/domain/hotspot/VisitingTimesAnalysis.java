package com.example.birdlensapi.domain.hotspot;

import java.util.Map;

public record VisitingTimesAnalysis(
        String locId,
        Map<Integer, Double> monthlyStats,
        Map<Integer, Double> hourlyStats
) {}