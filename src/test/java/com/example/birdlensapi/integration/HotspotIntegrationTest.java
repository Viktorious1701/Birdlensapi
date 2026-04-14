package com.example.birdlensapi.integration;

import com.example.birdlensapi.domain.auth.LoginRequest;
import com.example.birdlensapi.domain.hotspot.EbirdHotspot;
import com.example.birdlensapi.domain.hotspot.HotspotRepository;
import com.example.birdlensapi.domain.user.RegisterRequest;
import com.example.birdlensapi.domain.user.User;
import com.example.birdlensapi.domain.user.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class HotspotIntegrationTest extends AbstractIntegrationTest {

        @Autowired
        private WebTestClient client;

        // Converted to SpyBean so we can track method invocations to prove caching works
        @SpyBean
        private HotspotRepository hotspotRepository;

        @Autowired
        private UserRepository userRepository;

        private String validStandardJwtToken;
        private String validPremiumJwtToken;

        private final GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);

        @BeforeEach
        void setUp() {
                // Reset the SpyBean invocations before each test
                Mockito.reset(hotspotRepository);
                hotspotRepository.deleteAll();
                userRepository.deleteAll();

                // 1. Setup Standard User (No Subscription)
                RegisterRequest standardRegister = new RegisterRequest("standard@example.com", "standarduser", "securepass123");
                client.post().uri("/api/v1/auth/register")
                        .bodyValue(standardRegister)
                        .exchange()
                        .expectStatus().isCreated();

                LoginRequest standardLogin = new LoginRequest("standard@example.com", "securepass123");
                validStandardJwtToken = client.post().uri("/api/v1/auth/login")
                        .bodyValue(standardLogin)
                        .exchange()
                        .expectStatus().isOk()
                        .returnResult(JsonNode.class)
                        .getResponseBody()
                        .blockFirst()
                        .get("data").get("accessToken").asText();

                // 2. Setup Premium User (Active Subscription)
                RegisterRequest premiumRegister = new RegisterRequest("premium@example.com", "premiumuser", "securepass123");
                client.post().uri("/api/v1/auth/register")
                        .bodyValue(premiumRegister)
                        .exchange()
                        .expectStatus().isCreated();

                // Manually elevate the user to premium in the database
                User premiumUser = userRepository.findByEmail("premium@example.com").orElseThrow();
                premiumUser.setSubscriptionId(UUID.randomUUID());
                premiumUser.setSubscriptionExpiresAt(Instant.now().plus(30, ChronoUnit.DAYS));
                userRepository.save(premiumUser);

                LoginRequest premiumLogin = new LoginRequest("premium@example.com", "securepass123");
                validPremiumJwtToken = client.post().uri("/api/v1/auth/login")
                        .bodyValue(premiumLogin)
                        .exchange()
                        .expectStatus().isOk()
                        .returnResult(JsonNode.class)
                        .getResponseBody()
                        .blockFirst()
                        .get("data").get("accessToken").asText();

                // 3. Seed database with precise spatial testing data
                List<EbirdHotspot> mockHotspots = new ArrayList<>();

                // Center Point
                mockHotspots.add(createHotspot("L1", "Center Hotspot", 10.7769, 106.7009, 150));
                // Nearby Point (~4.4 km away from center)
                mockHotspots.add(createHotspot("L2", "Nearby Hotspot", 10.7369, 106.7009, 200));
                // Far Point (~111 km away from center)
                mockHotspots.add(createHotspot("L3", "Far Hotspot", 11.7769, 106.7009, 300));

                hotspotRepository.saveAll(mockHotspots);
        }

        private EbirdHotspot createHotspot(String id, String name, double lat, double lng, int speciesCount) {
                EbirdHotspot hotspot = new EbirdHotspot();
                hotspot.setLocId(id);
                hotspot.setLocName(name);
                hotspot.setLatitude(lat);
                hotspot.setLongitude(lng);
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
                client.get().uri("/api/v1/hotspots/nearby?lat=10.7769&lng=106.7009&radiusKm=10")
                        .header("Authorization", "Bearer " + validStandardJwtToken)
                        .exchange()
                        .expectStatus().isOk()
                        .expectBody()
                        .jsonPath("$.success").isEqualTo(true)
                        .jsonPath("$.data.length()").isEqualTo(2)
                        .jsonPath("$.data[0].locName").isEqualTo("Nearby Hotspot")
                        .jsonPath("$.data[1].locName").isEqualTo("Center Hotspot");
        }

        @Test
        void shouldReturnEmptyListIfRadiusTooSmall() {
                client.get().uri("/api/v1/hotspots/nearby?lat=10.7769&lng=106.7009&radiusKm=0.1")
                        .header("Authorization", "Bearer " + validStandardJwtToken)
                        .exchange()
                        .expectStatus().isOk()
                        .expectBody()
                        .jsonPath("$.success").isEqualTo(true)
                        .jsonPath("$.data.length()").isEqualTo(1)
                        .jsonPath("$.data[0].locName").isEqualTo("Center Hotspot");
        }

        @Test
        void shouldReturn400BadRequestForInvalidCoordinates() {
                client.get().uri("/api/v1/hotspots/nearby?lat=95.0&lng=106.7009&radiusKm=10")
                        .header("Authorization", "Bearer " + validStandardJwtToken)
                        .exchange()
                        .expectStatus().isBadRequest()
                        .expectBody()
                        .jsonPath("$.success").isEqualTo(false)
                        .jsonPath("$.error.code").isEqualTo("VALIDATION_ERROR");
        }

        @Test
        void shouldCacheNearbyHotspots() {
                // 1. First call - should hit DB
                client.get().uri("/api/v1/hotspots/nearby?lat=10.7769&lng=106.7009&radiusKm=10")
                        .header("Authorization", "Bearer " + validStandardJwtToken)
                        .exchange()
                        .expectStatus().isOk();

                // Verify repository was called exactly once
                verify(hotspotRepository, times(1)).findNearby(anyDouble(), anyDouble(), anyDouble());

                // 2. Second call - identical params
                client.get().uri("/api/v1/hotspots/nearby?lat=10.7769&lng=106.7009&radiusKm=10")
                        .header("Authorization", "Bearer " + validStandardJwtToken)
                        .exchange()
                        .expectStatus().isOk();

                // Verify repository is STILL only called once (served entirely from cache)
                verify(hotspotRepository, times(1)).findNearby(anyDouble(), anyDouble(), anyDouble());

                // 3. Third call - slightly different coordinates but same rounded value
                client.get().uri("/api/v1/hotspots/nearby?lat=10.781&lng=106.704&radiusKm=10")
                        .header("Authorization", "Bearer " + validStandardJwtToken)
                        .exchange()
                        .expectStatus().isOk();

                verify(hotspotRepository, times(1)).findNearby(anyDouble(), anyDouble(), anyDouble());
        }

        @Test
        void shouldReturnHotspotDetailsSuccessfully() {
                client.get().uri("/api/v1/hotspots/L1")
                        .header("Authorization", "Bearer " + validStandardJwtToken)
                        .exchange()
                        .expectStatus().isOk()
                        .expectBody()
                        .jsonPath("$.success").isEqualTo(true)
                        .jsonPath("$.data.locId").isEqualTo("L1")
                        .jsonPath("$.data.locName").isEqualTo("Center Hotspot")
                        .jsonPath("$.data.numSpeciesAllTime").isEqualTo(150);
        }

        @Test
        void shouldReturn404ForNonExistentHotspot() {
                client.get().uri("/api/v1/hotspots/L9999999")
                        .header("Authorization", "Bearer " + validStandardJwtToken)
                        .exchange()
                        .expectStatus().isNotFound()
                        .expectBody()
                        .jsonPath("$.success").isEqualTo(false)
                        .jsonPath("$.error.code").isEqualTo("RESOURCE_NOT_FOUND")
                        .jsonPath("$.error.message").isEqualTo("Hotspot with id 'L9999999' not found");
        }

        @Test
        void shouldReturn403ForbiddenWhenStandardUserRequestsVisitingTimes() {
                client.get().uri("/api/v1/hotspots/L1/visiting-times")
                        .header("Authorization", "Bearer " + validStandardJwtToken)
                        .exchange()
                        .expectStatus().isForbidden()
                        .expectBody()
                        .jsonPath("$.success").isEqualTo(false)
                        .jsonPath("$.error.code").isEqualTo("FORBIDDEN")
                        .jsonPath("$.error.message").isEqualTo("You do not have permission to access this resource.");
        }

        @Test
        void shouldReturnVisitingTimesWhenPremiumUserRequests() {
                client.get().uri("/api/v1/hotspots/L1/visiting-times")
                        .header("Authorization", "Bearer " + validPremiumJwtToken)
                        .exchange()
                        .expectStatus().isOk()
                        .expectBody()
                        .jsonPath("$.success").isEqualTo(true)
                        .jsonPath("$.data.locId").isEqualTo("L1")
                        // Verify the 12 months map exists
                        .jsonPath("$.data.monthlyStats.1").exists()
                        .jsonPath("$.data.monthlyStats.12").exists()
                        // Verify the 24 hours map exists
                        .jsonPath("$.data.hourlyStats.0").exists()
                        .jsonPath("$.data.hourlyStats.23").exists();
        }

        @Test
        void shouldReturn404WhenPremiumUserRequestsVisitingTimesForNonExistentHotspot() {
                client.get().uri("/api/v1/hotspots/L9999999/visiting-times")
                        .header("Authorization", "Bearer " + validPremiumJwtToken)
                        .exchange()
                        .expectStatus().isNotFound()
                        .expectBody()
                        .jsonPath("$.success").isEqualTo(false)
                        .jsonPath("$.error.code").isEqualTo("RESOURCE_NOT_FOUND")
                        .jsonPath("$.error.message").isEqualTo("Hotspot with id 'L9999999' not found");
        }
}