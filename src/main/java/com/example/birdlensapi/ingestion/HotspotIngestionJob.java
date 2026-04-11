package com.example.birdlensapi.ingestion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Date;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class HotspotIngestionJob {

    private static final Logger log = LoggerFactory.getLogger(HotspotIngestionJob.class);

    private final EbirdApiClient ebirdApiClient;
    private final JdbcTemplate jdbcTemplate;
    private final List<String> supportedRegions;

    public HotspotIngestionJob(EbirdApiClient ebirdApiClient,
                               JdbcTemplate jdbcTemplate,
                               @Value("${app.ebird.supported-regions}") List<String> supportedRegions) {
        this.ebirdApiClient = ebirdApiClient;
        this.jdbcTemplate = jdbcTemplate;
        this.supportedRegions = supportedRegions;
    }

    // Triggers automatically when the application is fully started
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ebird_hotspots", Integer.class);
        if (count != null && count == 0) {
            log.info("Hotspots table is empty. Triggering initial ingestion...");
            scheduledIngestion();
        }
    }

    // Runs every 6 hours
    @Scheduled(cron = "0 0 */6 * * *")
    public void scheduledIngestion() {
        log.info("Starting scheduled spatial hotspot ingestion for regions: {}", supportedRegions);

        for (String region : supportedRegions) {
            long startTime = System.currentTimeMillis();
            try {
                log.info("Fetching recent notable observations for region: {}", region);
                List<EbirdObservationDto> observations = ebirdApiClient.fetchRecentNotableObservations(region).block();

                if (observations != null && !observations.isEmpty()) {
                    List<EbirdObservationDto> deduplicatedHotspots = deduplicateObservationsToHotspots(observations);
                    log.info("Deduplicated {} raw observations to {} unique hotspots for region {}",
                            observations.size(), deduplicatedHotspots.size(), region);

                    upsertHotspots(deduplicatedHotspots);

                    long duration = System.currentTimeMillis() - startTime;
                    log.info("Successfully processed region {} in {} ms.", region, duration);
                } else {
                    log.info("No notable observations found for region: {}", region);
                }
            } catch (Exception e) {
                log.error("Failed to process hotspot ingestion for region {}. Error: {}", region, e.getMessage(), e);
            }
        }
        log.info("Completed scheduled spatial hotspot ingestion.");
    }

    public List<EbirdObservationDto> deduplicateObservationsToHotspots(List<EbirdObservationDto> observations) {
        Map<String, EbirdObservationDto> uniqueHotspots = new HashMap<>();

        for (EbirdObservationDto obs : observations) {
            if (obs.locId() == null || !obs.locId().startsWith("L")) {
                continue; // Only ingest formal eBird hotspots (IDs starting with 'L')
            }

            EbirdObservationDto existing = uniqueHotspots.get(obs.locId());
            if (existing == null) {
                uniqueHotspots.put(obs.locId(), obs);
            } else {
                // If the new observation is more recent, keep it instead
                if (obs.obsDt() != null && existing.obsDt() != null &&
                        obs.obsDt().compareTo(existing.obsDt()) > 0) {
                    uniqueHotspots.put(obs.locId(), obs);
                }
            }
        }
        return new ArrayList<>(uniqueHotspots.values());
    }

    private void upsertHotspots(List<EbirdObservationDto> hotspots) {
        String sql = """
            INSERT INTO ebird_hotspots (loc_id, loc_name, latitude, longitude, location, latest_obs_dt)
            VALUES (?, ?, ?, ?, ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography, ?)
            ON CONFLICT (loc_id) DO UPDATE SET
                loc_name = EXCLUDED.loc_name,
                latitude = EXCLUDED.latitude,
                longitude = EXCLUDED.longitude,
                location = EXCLUDED.location,
                latest_obs_dt = GREATEST(ebird_hotspots.latest_obs_dt, EXCLUDED.latest_obs_dt)
            """;

        jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                EbirdObservationDto dto = hotspots.get(i);

                ps.setString(1, dto.locId());
                ps.setString(2, dto.locName());
                ps.setDouble(3, dto.lat());
                ps.setDouble(4, dto.lng());

                // ST_MakePoint requires (longitude, latitude) exactly in this order (X, Y)
                ps.setDouble(5, dto.lng());
                ps.setDouble(6, dto.lat());

                // eBird obsDt format is typically "yyyy-MM-dd HH:mm". We only need the date part.
                Date sqlDate = null;
                if (dto.obsDt() != null && dto.obsDt().length() >= 10) {
                    try {
                        sqlDate = Date.valueOf(dto.obsDt().substring(0, 10));
                    } catch (IllegalArgumentException e) {
                        log.warn("Failed to parse date {} for hotspot {}", dto.obsDt(), dto.locId());
                    }
                }
                ps.setDate(7, sqlDate);
            }

            @Override
            public int getBatchSize() {
                return hotspots.size();
            }
        });
    }
}