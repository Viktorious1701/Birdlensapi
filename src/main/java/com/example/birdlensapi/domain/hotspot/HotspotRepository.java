package com.example.birdlensapi.domain.hotspot;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface HotspotRepository extends JpaRepository<EbirdHotspot, String> {
}