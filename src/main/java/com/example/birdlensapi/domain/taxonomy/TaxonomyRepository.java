package com.example.birdlensapi.domain.taxonomy;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TaxonomyRepository extends JpaRepository<BirdTaxonomy, String> {

    @Query(value = """
        SELECT * FROM bird_taxonomy
        WHERE common_name ILIKE CONCAT('%', :query, '%') 
           OR scientific_name ILIKE CONCAT('%', :query, '%')
        ORDER BY taxon_order ASC
        LIMIT 15
        """, nativeQuery = true)
    List<BirdTaxonomy> searchTaxonomy(@Param("query") String query);
}