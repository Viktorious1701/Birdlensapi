package com.example.birdlensapi.domain.hotspot;

import com.example.birdlensapi.common.exception.ResourceNotFoundException;
import com.example.birdlensapi.domain.user.User;
import com.example.birdlensapi.domain.user.UserRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

@Service
public class HotspotService {

    private final HotspotRepository hotspotRepository;
    private final UserRepository userRepository;

    public HotspotService(HotspotRepository hotspotRepository, UserRepository userRepository) {
        this.hotspotRepository = hotspotRepository;
        this.userRepository = userRepository;
    }

    // Cache key rounds to 2 decimal places (roughly 1.1km grid) to maximize cache hit ratio
    @Cacheable(value = "hotspots", key = "T(java.lang.Math).round(#lat * 100.0) / 100.0 + ':' + T(java.lang.Math).round(#lng * 100.0) / 100.0 + ':' + #radiusKm")
    @Transactional(readOnly = true)
    public List<EbirdNearbyHotspot> getNearbyHotspots(double lat, double lng, double radiusKm) {
        if (radiusKm <= 0) {
            return List.of();
        }

        // PostGIS ST_DWithin expects distance in meters for GEOGRAPHY columns
        double radiusMeters = radiusKm * 1000.0;

        return hotspotRepository.findNearby(lat, lng, radiusMeters)
                .stream()
                .map(EbirdNearbyHotspot::fromEntity)
                .collect(Collectors.toList());
    }

    @Cacheable(value = "hotspot_details", key = "#locId")
    @Transactional(readOnly = true)
    public EbirdNearbyHotspot getHotspotDetails(String locId) {
        return hotspotRepository.findById(locId)
                .map(EbirdNearbyHotspot::fromEntity)
                .orElseThrow(() -> new ResourceNotFoundException("Hotspot with id '" + locId + "' not found"));
    }

    @Transactional(readOnly = true)
    public VisitingTimesAnalysis getVisitingTimes(String locId, String userEmail) {
        // 1. Verify user exists and has an active premium subscription
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new AccessDeniedException("User not found"));

        if (user.getSubscriptionId() == null ||
                user.getSubscriptionExpiresAt() == null ||
                user.getSubscriptionExpiresAt().isBefore(Instant.now())) {
            throw new AccessDeniedException("Active premium subscription required to view analytical visiting times.");
        }

        // 2. Verify hotspot exists
        hotspotRepository.findById(locId)
                .orElseThrow(() -> new ResourceNotFoundException("Hotspot with id '" + locId + "' not found"));

        // 3. Generate deterministic mock data based on the hotspot's locId
        return generateMockVisitingTimes(locId);
    }

    private VisitingTimesAnalysis generateMockVisitingTimes(String locId) {
        // Seed the random generator with the locId so the same hotspot always returns the same chart data
        Random random = new Random(locId.hashCode());

        Map<Integer, Double> monthlyStats = new HashMap<>();
        for (int month = 1; month <= 12; month++) {
            // Generate a percentage score between 0.0 and 1.0
            double score = Math.round(random.nextDouble() * 100.0) / 100.0;
            monthlyStats.put(month, score);
        }

        Map<Integer, Double> hourlyStats = new HashMap<>();
        for (int hour = 0; hour <= 23; hour++) {
            // Generate a percentage score between 0.0 and 1.0
            double score = Math.round(random.nextDouble() * 100.0) / 100.0;
            hourlyStats.put(hour, score);
        }

        return new VisitingTimesAnalysis(locId, monthlyStats, hourlyStats);
    }
}