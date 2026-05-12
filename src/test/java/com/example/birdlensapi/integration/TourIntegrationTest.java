package com.example.birdlensapi.integration;

import com.example.birdlensapi.domain.auth.LoginRequest;
import com.example.birdlensapi.domain.tour.Event;
import com.example.birdlensapi.domain.tour.EventRepository;
import com.example.birdlensapi.domain.tour.Tour;
import com.example.birdlensapi.domain.tour.TourRepository;
import com.example.birdlensapi.domain.user.RegisterRequest;
import com.example.birdlensapi.domain.user.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

class TourIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private WebTestClient client;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private TourRepository tourRepository;

    @Autowired
    private UserRepository userRepository;

    private String validJwtToken;

    @BeforeEach
    void setUp() {
        tourRepository.deleteAll();
        eventRepository.deleteAll();
        userRepository.deleteAll();

        // 1. Setup Auth
        RegisterRequest register = new RegisterRequest("touruser@example.com", "touruser", "securepass123");
        client.post().uri("/api/v1/auth/register")
                .bodyValue(register)
                .exchange()
                .expectStatus().isCreated();

        LoginRequest login = new LoginRequest("touruser@example.com", "securepass123");
        validJwtToken = client.post().uri("/api/v1/auth/login")
                .bodyValue(login)
                .exchange()
                .expectStatus().isOk()
                .returnResult(JsonNode.class)
                .getResponseBody()
                .blockFirst()
                .get("data").get("accessToken").asText();

        // 2. Seed Database with 3 Events and 5 Tours
        for (int i = 1; i <= 3; i++) {
            Event event = new Event();
            event.setTitle("Birding Event " + i);
            event.setDescription("Annual bird watching gathering");
            // Set staggered start dates for sorting verification
            event.setStartDate(Instant.now().plus(i * 10L, ChronoUnit.DAYS));
            event.setEndDate(Instant.now().plus((i * 10L) + 2, ChronoUnit.DAYS));
            event = eventRepository.save(event);

            // Give the first event 3 tours, and the second event 2 tours (total 5)
            if (i == 1) {
                for (int t = 1; t <= 3; t++) {
                    createTour(event, "Tour " + t, "99.99");
                }
            } else if (i == 2) {
                for (int t = 4; t <= 5; t++) {
                    createTour(event, "Tour " + t, "149.99");
                }
            }
        }
    }

    private void createTour(Event event, String name, String priceStr) {
        Tour tour = new Tour();
        tour.setEvent(event);
        tour.setName(name);
        tour.setPrice(new BigDecimal(priceStr));
        tour.setCapacity(20);
        tour.setDurationHours(4);
        tourRepository.save(tour);
    }

    @Test
    void shouldReturn401WhenNoTokenProvided() {
        client.get().uri("/api/v1/events")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void shouldFetchPaginatedEventsCorrectly() {
        // We have 3 events. Fetching size 2 should return 2 elements and page metadata.
        client.get().uri("/api/v1/events?page=0&size=2")
                .header("Authorization", "Bearer " + validJwtToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data.content.length()").isEqualTo(2)
                .jsonPath("$.data.totalElements").isEqualTo(3)
                .jsonPath("$.data.totalPages").isEqualTo(2)
                .jsonPath("$.data.isLast").isEqualTo(false)
                // Due to default ASC sorting on startDate, Event 1 should be first
                .jsonPath("$.data.content[0].title").isEqualTo("Birding Event 1");
    }

    @Test
    void shouldFetchPaginatedToursCorrectly() {
        // We have 5 tours. Fetching size 2 on page 1 (the second page) should return 2 tours.
        client.get().uri("/api/v1/tours?page=1&size=2")
                .header("Authorization", "Bearer " + validJwtToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data.content.length()").isEqualTo(2)
                .jsonPath("$.data.totalElements").isEqualTo(5)
                .jsonPath("$.data.totalPages").isEqualTo(3)
                // Verify the DTO is mapping event ID properly without the entire event object payload
                .jsonPath("$.data.content[0].eventId").exists()
                .jsonPath("$.data.content[0].price").exists();
    }
}