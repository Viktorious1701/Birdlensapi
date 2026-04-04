package com.example.birdlensapi.unit;

import com.example.birdlensapi.ingestion.EbirdApiClient;
import com.example.birdlensapi.ingestion.EbirdObservationDto;
import com.example.birdlensapi.ingestion.HotspotIngestionJob;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import reactor.core.publisher.Mono;

import java.sql.PreparedStatement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class HotspotIngestionJobTest {

    private EbirdApiClient ebirdApiClient;
    private JdbcTemplate jdbcTemplate;
    private HotspotIngestionJob ingestionJob;

    @BeforeEach
    void setUp() {
        ebirdApiClient = mock(EbirdApiClient.class);
        jdbcTemplate = mock(JdbcTemplate.class);
        ingestionJob = new HotspotIngestionJob(ebirdApiClient, jdbcTemplate, List.of("US"));
    }

    @Test
    void shouldDeduplicateObservationsCorrectly() {
        // Arrange: Multiple sightings at Central Park (L123), plus one personal location (P456)
        List<EbirdObservationDto> rawObs = List.of(
                new EbirdObservationDto("L123", "Central Park", 40.7, -73.9, "2026-03-24 08:00"),
                new EbirdObservationDto("L123", "Central Park", 40.7, -73.9, "2026-03-24 14:00"), // Most recent
                new EbirdObservationDto("L123", "Central Park", 40.7, -73.9, "2026-03-23 10:00"),
                new EbirdObservationDto("L999", "Lake", 41.0, -74.0, "2026-03-20 10:00"),
                new EbirdObservationDto("P456", "Personal Yard", 40.0, -73.0, "2026-03-24 10:00") // Should be ignored
        );

        // Act
        List<EbirdObservationDto> result = ingestionJob.deduplicateObservationsToHotspots(rawObs);

        // Assert: Personal location 'P456' is dropped. 'L123' collapses to 1 record. 'L999' remains. Total = 2.
        assertEquals(2, result.size());

        EbirdObservationDto centralPark = result.stream()
                .filter(h -> h.locId().equals("L123"))
                .findFirst()
                .orElseThrow();

        // Assert it kept the most recent date
        assertEquals("2026-03-24 14:00", centralPark.obsDt());
    }

    @Test
    void shouldInjectPreparedStatementInCorrectSpatialOrder() throws Exception {
        // Arrange
        List<EbirdObservationDto> mockObs = List.of(
                new EbirdObservationDto("L123", "Central Park", 40.78, -73.96, "2026-03-24 14:00")
        );
        when(ebirdApiClient.fetchRecentNotableObservations("US")).thenReturn(Mono.just(mockObs));

        // Act
        ingestionJob.scheduledIngestion();

        // Assert & Capture the Setter
        ArgumentCaptor<BatchPreparedStatementSetter> setterCaptor = ArgumentCaptor.forClass(BatchPreparedStatementSetter.class);
        verify(jdbcTemplate).batchUpdate(anyString(), setterCaptor.capture());

        BatchPreparedStatementSetter setter = setterCaptor.getValue();
        PreparedStatement ps = mock(PreparedStatement.class);

        setter.setValues(ps, 0);

        // Ensure Longitude (-73.96) is Parameter 5 (X) and Latitude (40.78) is Parameter 6 (Y) for ST_MakePoint
        verify(ps).setDouble(5, -73.96);
        verify(ps).setDouble(6, 40.78);
        verify(ps).setDate(7, java.sql.Date.valueOf("2026-03-24"));
    }
}