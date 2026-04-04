package com.example.birdlensapi.integration;

import com.example.birdlensapi.domain.auth.LoginRequest;
import com.example.birdlensapi.domain.taxonomy.BirdTaxonomy;
import com.example.birdlensapi.domain.taxonomy.TaxonomyRepository;
import com.example.birdlensapi.domain.user.RegisterRequest;
import com.example.birdlensapi.domain.user.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

class TaxonomyIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private WebTestClient client;

    @Autowired
    private TaxonomyRepository taxonomyRepository;

    @Autowired
    private UserRepository userRepository;

    private String validJwtToken;

    @BeforeEach
    void setUp() {
        taxonomyRepository.deleteAll();
        userRepository.deleteAll();

        // 1. Register and login to get a valid JWT for testing secured endpoints
        RegisterRequest register = new RegisterRequest("taxo@example.com", "taxouser", "securepass123");
        client.post().uri("/api/v1/auth/register")
                .bodyValue(register)
                .exchange()
                .expectStatus().isCreated();

        LoginRequest login = new LoginRequest("taxo@example.com", "securepass123");
        validJwtToken = client.post().uri("/api/v1/auth/login")
                .bodyValue(login)
                .exchange()
                .expectStatus().isOk()
                .returnResult(JsonNode.class)
                .getResponseBody()
                .blockFirst()
                .get("data").get("accessToken").asText();

        // 2. Seed database with mock taxonomy data
        List<BirdTaxonomy> mockBirds = new ArrayList<>();

        BirdTaxonomy bird1 = new BirdTaxonomy();
        bird1.setSpeciesCode("baleag");
        bird1.setCommonName("Bald Eagle");
        bird1.setScientificName("Haliaeetus leucocephalus");
        bird1.setTaxonOrder(BigDecimal.valueOf(10.5));
        mockBirds.add(bird1);

        BirdTaxonomy bird2 = new BirdTaxonomy();
        bird2.setSpeciesCode("goleag");
        bird2.setCommonName("Golden Eagle");
        bird2.setScientificName("Aquila chrysaetos");
        bird2.setTaxonOrder(BigDecimal.valueOf(11.0));
        mockBirds.add(bird2);

        BirdTaxonomy bird3 = new BirdTaxonomy();
        bird3.setSpeciesCode("mallar3");
        bird3.setCommonName("Mallard");
        bird3.setScientificName("Anas platyrhynchos");
        bird3.setTaxonOrder(BigDecimal.valueOf(5.0));
        mockBirds.add(bird3);

        // Add 15 extra mock eagles to test the limit constraint
        for (int i = 0; i < 15; i++) {
            BirdTaxonomy extraEagle = new BirdTaxonomy();
            extraEagle.setSpeciesCode("eagle" + i);
            extraEagle.setCommonName("Test Eagle " + i);
            extraEagle.setScientificName("FakeEagle " + i);
            extraEagle.setTaxonOrder(BigDecimal.valueOf(100.0 + i));
            mockBirds.add(extraEagle);
        }

        taxonomyRepository.saveAll(mockBirds);
    }

    @Test
    void shouldReturn401WhenNoTokenProvided() {
        client.get().uri("/api/v1/taxonomy/search?q=eagle")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void shouldFindBirdsByCommonNameCaseInsensitive() {
        client.get().uri("/api/v1/taxonomy/search?q=EaGlE")
                .header("Authorization", "Bearer " + validJwtToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                // "Bald Eagle", "Golden Eagle", and 13 "Test Eagle"s due to LIMIT 15
                .jsonPath("$.data.length()").isEqualTo(15)
                .jsonPath("$.data[0].commonName").isEqualTo("Bald Eagle") // Verify ordering by taxon_order
                .jsonPath("$.data[1].commonName").isEqualTo("Golden Eagle");
    }

    @Test
    void shouldFindBirdsByScientificName() {
        client.get().uri("/api/v1/taxonomy/search?q=haliaeetus")
                .header("Authorization", "Bearer " + validJwtToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data.length()").isEqualTo(1)
                .jsonPath("$.data[0].commonName").isEqualTo("Bald Eagle");
    }

    @Test
    void shouldReturnEmptyListForEmptyQuery() {
        client.get().uri("/api/v1/taxonomy/search?q= ")
                .header("Authorization", "Bearer " + validJwtToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data.length()").isEqualTo(0);
    }
}