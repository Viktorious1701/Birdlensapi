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

    @Cacheable(value = "hotspots", key = "T(java.lang.Math).round(#lat * 100.0) / 100.0 + ':' + T(java.lang.Math).round(#lng * 100.0) / 100.0 + ':' + #radiusKm")
    @Transactional(readOnly = true)
    public List<EbirdNearbyHotspot> getNearbyHotspots(double lat, double lng, double radiusKm) {
        if (radiusKm <= 0) {
            return List.of();
        }

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
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new AccessDeniedException("User not found"));

        // Refactored to check the new ManyToOne subscription mapping
        if (user.getSubscription() == null ||
                user.getSubscriptionExpiresAt() == null ||
                user.getSubscriptionExpiresAt().isBefore(Instant.now())) {
            throw new AccessDeniedException("Active premium subscription required to view analytical visiting times.");
        }

        hotspotRepository.findById(locId)
                .orElseThrow(() -> new ResourceNotFoundException("Hotspot with id '" + locId + "' not found"));

        return generateMockVisitingTimes(locId);
    }

    private VisitingTimesAnalysis generateMockVisitingTimes(String locId) {
        Random random = new Random(locId.hashCode());

        Map<Integer, Double> monthlyStats = new HashMap<>();
        for (int month = 1; month <= 12; month++) {
            double score = Math.round(random.nextDouble() * 100.0) / 100.0;
            monthlyStats.put(month, score);
        }

        Map<Integer, Double> hourlyStats = new HashMap<>();
        for (int hour = 0; hour <= 23; hour++) {
            double score = Math.round(random.nextDouble() * 100.0) / 100.0;
            hourlyStats.put(hour, score);
        }

        return new VisitingTimesAnalysis(locId, monthlyStats, hourlyStats);
    }
}