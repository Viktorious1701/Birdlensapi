package com.example.birdlensapi.ingestion;

import java.math.BigDecimal;

public record TaxonomyDto(
        String speciesCode,
        String comName,
        String sciName,
        String category,
        BigDecimal taxonOrder,
        String order,
        String familyComName,
        String familySciName
) {}