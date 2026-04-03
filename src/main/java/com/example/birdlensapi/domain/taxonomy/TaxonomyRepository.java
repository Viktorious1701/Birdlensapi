package com.example.birdlensapi.domain.taxonomy;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TaxonomyRepository extends JpaRepository<BirdTaxonomy, String> {
}