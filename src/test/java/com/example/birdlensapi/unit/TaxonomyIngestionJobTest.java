package com.example.birdlensapi.unit;

import com.example.birdlensapi.ingestion.EbirdApiClient;
import com.example.birdlensapi.ingestion.TaxonomyDto;
import com.example.birdlensapi.ingestion.TaxonomyIngestionJob;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TaxonomyIngestionJobTest {

    private EbirdApiClient ebirdApiClient;
    private JdbcTemplate jdbcTemplate;
    private TaxonomyIngestionJob ingestionJob;

    @BeforeEach
    void setUp() {
        ebirdApiClient = mock(EbirdApiClient.class);
        jdbcTemplate = mock(JdbcTemplate.class);
        ingestionJob = new TaxonomyIngestionJob(ebirdApiClient, jdbcTemplate);
    }

    @Test
    void shouldProcessInBatchesAndContinueOnPartialFailure() {
        // 1. Arrange
        List<TaxonomyDto> mockData = new ArrayList<>();
        // Create 1000 dummy records (Exactly 2 batches of 500)
        for (int i = 0; i < 1000; i++) {
            mockData.add(new TaxonomyDto("code" + i, "com", "sci", "cat", BigDecimal.ONE, "order", "famCom", "famSci"));
        }
        when(ebirdApiClient.fetchTaxonomy()).thenReturn(Mono.just(mockData));

        // We simulate a scenario where the FIRST batch throws a Database Exception,
        // but we want to ensure the SECOND batch is still executed (partial failure resiliency).
        doThrow(new RuntimeException("Simulated Database Failure for Batch 1"))
                .doNothing()
                .when(jdbcTemplate).batchUpdate(any(String.class), any(BatchPreparedStatementSetter.class));

        // 2. Act
        ingestionJob.ingestTaxonomy();

        // 3. Assert
        // Verify the API client was called exactly once
        verify(ebirdApiClient, times(1)).fetchTaxonomy();

        // Verify batchUpdate was attempted exactly TWICE, proving that the try-catch block
        // successfully suppressed the failure on the first batch and allowed the second batch to run.
        verify(jdbcTemplate, times(2)).batchUpdate(any(String.class), any(BatchPreparedStatementSetter.class));
    }
}