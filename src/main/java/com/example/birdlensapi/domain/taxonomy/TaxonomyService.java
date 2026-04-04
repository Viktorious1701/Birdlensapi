package com.example.birdlensapi.domain.taxonomy;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class TaxonomyService {

    private final TaxonomyRepository taxonomyRepository;

    public TaxonomyService(TaxonomyRepository taxonomyRepository) {
        this.taxonomyRepository = taxonomyRepository;
    }

    @Transactional(readOnly = true)
    public List<TaxonomySearchResponse> search(String query) {
        if (query == null || query.trim().isEmpty()) {
            return List.of();
        }

        return taxonomyRepository.searchTaxonomy(query.trim())
                .stream()
                .map(TaxonomySearchResponse::fromEntity)
                .collect(Collectors.toList());
    }
}