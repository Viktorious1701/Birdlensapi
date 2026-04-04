package com.example.birdlensapi.ingestion;

public record EbirdObservationDto(
        String locId,
        String locName,
        Double lat,
        Double lng,
        String obsDt
) {}