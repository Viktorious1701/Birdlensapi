package com.example.birdlensapi.ingestion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

@Service
public class TaxonomyIngestionJob {

    private static final Logger log = LoggerFactory.getLogger(TaxonomyIngestionJob.class);
    private static final int BATCH_SIZE = 500;

    private final EbirdApiClient ebirdApiClient;
    private final JdbcTemplate jdbcTemplate;

    public TaxonomyIngestionJob(EbirdApiClient ebirdApiClient, JdbcTemplate jdbcTemplate) {
        this.ebirdApiClient = ebirdApiClient;
        this.jdbcTemplate = jdbcTemplate;
    }

    // Triggers automatically when the application is fully started
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM bird_taxonomy", Integer.class);
        if (count != null && count == 0) {
            log.info("Taxonomy table is empty. Triggering initial ingestion...");
            ingestTaxonomy();
        }
    }

    // Runs weekly on Sunday at midnight (server time)
    @Scheduled(cron = "0 0 0 * * SUN")
    public void scheduledIngestion() {
        log.info("Running scheduled weekly taxonomy ingestion...");
        ingestTaxonomy();
    }

    public void ingestTaxonomy() {
        long startTime = System.currentTimeMillis();
        try {
            log.info("Fetching taxonomy data from eBird API...");

            // Block is safe here because this runs asynchronously in a scheduled thread
            List<TaxonomyDto> taxonomyList = ebirdApiClient.fetchTaxonomy().block();

            if (taxonomyList == null || taxonomyList.isEmpty()) {
                log.warn("Received empty taxonomy list from eBird API. Aborting ingestion.");
                return;
            }

            log.info("Successfully fetched {} taxonomy records. Starting batch UPSERT...", taxonomyList.size());

            int totalProcessed = 0;

            // Partition list into chunks to prevent overwhelming the database batch pipeline
            for (int i = 0; i < taxonomyList.size(); i += BATCH_SIZE) {
                int end = Math.min(i + BATCH_SIZE, taxonomyList.size());
                List<TaxonomyDto> batch = taxonomyList.subList(i, end);

                try {
                    upsertBatch(batch);
                    totalProcessed += batch.size();
                } catch (Exception e) {
                    log.error("Failed to process batch starting at index {}. Continuing with remaining batches. Error: {}", i, e.getMessage());
                }
            }

            long duration = System.currentTimeMillis() - startTime;
            log.info("Taxonomy ingestion completed. Successfully processed {} records in {} ms.", totalProcessed, duration);

        } catch (Exception e) {
            log.error("Fatal error occurred during taxonomy ingestion job", e);
        }
    }

    private void upsertBatch(List<TaxonomyDto> batch) {
        String sql = """
            INSERT INTO bird_taxonomy (species_code, common_name, scientific_name, category, taxon_order, bird_order, family_common_name, family_scientific_name)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (species_code) DO UPDATE SET
                common_name = EXCLUDED.common_name,
                scientific_name = EXCLUDED.scientific_name,
                category = EXCLUDED.category,
                taxon_order = EXCLUDED.taxon_order,
                bird_order = EXCLUDED.bird_order,
                family_common_name = EXCLUDED.family_common_name,
                family_scientific_name = EXCLUDED.family_scientific_name
            """;

        jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                TaxonomyDto dto = batch.get(i);
                ps.setString(1, dto.speciesCode());
                ps.setString(2, dto.comName());
                ps.setString(3, dto.sciName());
                ps.setString(4, dto.category());
                ps.setBigDecimal(5, dto.taxonOrder());
                ps.setString(6, dto.order());
                ps.setString(7, dto.familyComName());
                ps.setString(8, dto.familySciName());
            }

            @Override
            public int getBatchSize() {
                return batch.size();
            }
        });
    }
}