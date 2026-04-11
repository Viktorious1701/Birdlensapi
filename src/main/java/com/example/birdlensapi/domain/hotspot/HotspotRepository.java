package com.example.birdlensapi.domain.hotspot;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface HotspotRepository extends JpaRepository<EbirdHotspot, String> {

    @Query(value = """
        SELECT * FROM ebird_hotspots
        WHERE ST_DWithin(location, ST_MakePoint(:lng, :lat)::geography, :radiusMeters)
        ORDER BY num_species_all_time DESC
        LIMIT 50
        """, nativeQuery = true)
    List<EbirdHotspot> findNearby(
            @Param("lat") double lat,
            @Param("lng") double lng,
            @Param("radiusMeters") double radiusMeters
    );
}