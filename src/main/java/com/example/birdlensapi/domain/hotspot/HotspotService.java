package com.example.birdlensapi.domain.hotspot;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class HotspotService {

    private final HotspotRepository hotspotRepository;

    public HotspotService(HotspotRepository hotspotRepository) {
        this.hotspotRepository = hotspotRepository;
    }

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
}