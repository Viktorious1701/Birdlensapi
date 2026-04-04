package com.example.birdlensapi.domain.taxonomy;

public record TaxonomySearchResponse(
        String speciesCode,
        String commonName,
        String scientificName
) {
    public static TaxonomySearchResponse fromEntity(BirdTaxonomy taxonomy) {
        return new TaxonomySearchResponse(
                taxonomy.getSpeciesCode(),
                taxonomy.getCommonName(),
                taxonomy.getScientificName()
        );
    }
}