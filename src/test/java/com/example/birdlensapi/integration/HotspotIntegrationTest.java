package com.example.birdlensapi.integration;

import com.example.birdlensapi.domain.auth.LoginRequest;
import com.example.birdlensapi.domain.hotspot.EbirdHotspot;
import com.example.birdlensapi.domain.hotspot.HotspotRepository;
import com.example.birdlensapi.domain.user.RegisterRequest;
import com.example.birdlensapi.domain.user.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

class HotspotIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private WebTestClient client;

    @Autowired
    private HotspotRepository hotspotRepository;

    @Autowired
    private UserRepository userRepository;

    private String validJwtToken;
    private final GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);

    @BeforeEach
    void setUp() {
        hotspotRepository.deleteAll();
        userRepository.deleteAll();

        // 1. Register and login to acquire a valid JWT
        RegisterRequest register = new RegisterRequest("hotspot@example.com", "hotspotuser", "securepass123");
        client.post().uri("/api/v1/auth/register")
                .bodyValue(register)
                .exchange()
                .expectStatus().isCreated();

        LoginRequest login = new LoginRequest("hotspot@example.com", "securepass123");
        validJwtToken = client.post().uri("/api/v1/auth/login")
                .bodyValue(login)
                .exchange()
                .expectStatus().isOk()
                .returnResult(JsonNode.class)
                .getResponseBody()
                .blockFirst()
                .get("data").get("accessToken").asText();

        // 2. Seed database with precise spatial testing data
        List<EbirdHotspot> mockHotspots = new ArrayList<>();

        // Center Point (e.g., Ho Chi Minh City center)
        // Lat: 10.7769, Lng: 106.7009
        mockHotspots.add(createHotspot("L1", "Center Hotspot", 10.7769, 106.7009, 150));

        // Nearby Point (~4.4 km away from center)
        // Lat: 10.7369, Lng: 106.7009
        mockHotspots.add(createHotspot("L2", "Nearby Hotspot", 10.7369, 106.7009, 200));

        // Far Point (~111 km away from center)
        // Lat: 11.7769, Lng: 106.7009
        mockHotspots.add(createHotspot("L3", "Far Hotspot", 11.7769, 106.7009, 300));

        hotspotRepository.saveAll(mockHotspots);
    }

    private EbirdHotspot createHotspot(String id, String name, double lat, double lng, int speciesCount) {
        EbirdHotspot hotspot = new EbirdHotspot();
        hotspot.setLocId(id);
        hotspot.setLocName(name);
        hotspot.setLatitude(lat);
        hotspot.setLongitude(lng);
        // Note: Coordinate requires (X, Y) which translates to (Longitude, Latitude)
        Point point = geometryFactory.createPoint(new Coordinate(lng, lat));
        hotspot.setLocation(point);
        hotspot.setNumSpeciesAllTime(speciesCount);
        hotspot.setLatestObsDt(LocalDate.now());
        return hotspot;
    }

    @Test
    void shouldReturn401WhenNoTokenProvided() {
        client.get().uri("/api/v1/hotspots/nearby?lat=10.7769&lng=106.7009&radiusKm=10")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void shouldReturnOnlyNearbyHotspotsOrderedBySpeciesCount() {
        // Querying with a 10km radius from the Center Point.
        // It should find "Center Hotspot" and "Nearby Hotspot" (4.4km away).
        // It should exclude "Far Hotspot" (111km away).
        client.get().uri("/api/v1/hotspots/nearby?lat=10.7769&lng=106.7009&radiusKm=10")
                .header("Authorization", "Bearer " + validJwtToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data.length()").isEqualTo(2)
                // Verify order by numSpeciesAllTime DESC
                .jsonPath("$.data[0].locName").isEqualTo("Nearby Hotspot") // Has 200 species
                .jsonPath("$.data[1].locName").isEqualTo("Center Hotspot"); // Has 150 species
    }

    @Test
    void shouldReturnEmptyListIfRadiusTooSmall() {
        // Querying with a 0.1km (100 meter) radius. Should only return the exact center point.
        client.get().uri("/api/v1/hotspots/nearby?lat=10.7769&lng=106.7009&radiusKm=0.1")
                .header("Authorization", "Bearer " + validJwtToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data.length()").isEqualTo(1)
                .jsonPath("$.data[0].locName").isEqualTo("Center Hotspot");
    }

    @Test
    void shouldReturn400BadRequestForInvalidCoordinates() {
        // Latitude > 90 is invalid
        client.get().uri("/api/v1/hotspots/nearby?lat=95.0&lng=106.7009&radiusKm=10")
                .header("Authorization", "Bearer " + validJwtToken)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.success").isEqualTo(false)
                .jsonPath("$.error.code").isEqualTo("VALIDATION_ERROR");
    }
}