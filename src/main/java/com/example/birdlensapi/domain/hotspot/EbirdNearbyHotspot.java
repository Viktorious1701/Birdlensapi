package com.example.birdlensapi.domain.hotspot;

import java.time.LocalDate;

public record EbirdNearbyHotspot(
        String locId,
        String locName,
        Double lat,
        Double lng,
        LocalDate latestObsDt,
        Integer numSpeciesAllTime
) {
    public static EbirdNearbyHotspot fromEntity(EbirdHotspot hotspot) {
        return new EbirdNearbyHotspot(
                hotspot.getLocId(),
                hotspot.getLocName(),
                hotspot.getLatitude(),
                hotspot.getLongitude(),
                hotspot.getLatestObsDt(),
                hotspot.getNumSpeciesAllTime()
        );
    }
}