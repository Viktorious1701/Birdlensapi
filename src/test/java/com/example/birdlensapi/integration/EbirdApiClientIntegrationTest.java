package com.example.birdlensapi.integration;

import com.example.birdlensapi.ingestion.EbirdApiClient;
import com.example.birdlensapi.ingestion.TaxonomyDto;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EbirdApiClientIntegrationTest extends AbstractIntegrationTest {

    private static MockWebServer mockWebServer;

    @Autowired
    private EbirdApiClient ebirdApiClient;

    @BeforeAll
    static void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
    }

    @AfterAll
    static void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("app.ebird.base-url", () -> mockWebServer.url("/").toString());
        registry.add("app.ebird.api-key", () -> "test-ebird-key");
    }

    @Test
    void shouldFetchTaxonomySuccessfullyAndInjectHeader() throws InterruptedException {
        // Enqueue a mocked JSON response matching the fields of TaxonomyDto
        String mockJsonResponse = "[{\"speciesCode\":\"mallar3\",\"comName\":\"Mallard\",\"sciName\":\"Anas platyrhynchos\",\"category\":\"species\",\"taxonOrder\":201.5,\"order\":\"Anseriformes\",\"familyComName\":\"Ducks, Geese, and Waterfowl\",\"familySciName\":\"Anatidae\"}]";

        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(mockJsonResponse));

        StepVerifier.create(ebirdApiClient.fetchTaxonomy())
                .expectNextMatches(list -> list.size() == 1 &&
                        list.get(0).speciesCode().equals("mallar3") &&
                        list.get(0).taxonOrder().compareTo(BigDecimal.valueOf(201.5)) == 0)
                .verifyComplete();

        RecordedRequest request = mockWebServer.takeRequest();
        assertEquals("test-ebird-key", request.getHeader("x-ebirdapitoken"));
        assertEquals("/ref/taxonomy/ebird?fmt=json", request.getPath());
    }

    @Test
    void shouldRetryOn503AndRecover() {
        String mockJsonResponse = "[{\"speciesCode\":\"recovered\",\"comName\":\"Recovered Bird\",\"sciName\":\"Aves recovered\",\"category\":\"species\",\"taxonOrder\":1.0,\"order\":\"Testiformes\",\"familyComName\":\"Testidae\",\"familySciName\":\"Testidae\"}]";

        mockWebServer.enqueue(new MockResponse().setResponseCode(503));
        mockWebServer.enqueue(new MockResponse().setResponseCode(503));
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(mockJsonResponse));

        StepVerifier.create(ebirdApiClient.fetchTaxonomy())
                .expectNextMatches(list -> list.size() == 1 && list.get(0).speciesCode().equals("recovered"))
                .verifyComplete();
    }

    @Test
    void shouldFailAfterMaxRetries() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(503));
        mockWebServer.enqueue(new MockResponse().setResponseCode(503));
        mockWebServer.enqueue(new MockResponse().setResponseCode(503));
        mockWebServer.enqueue(new MockResponse().setResponseCode(503)); // 4th attempt fails

        StepVerifier.create(ebirdApiClient.fetchTaxonomy())
                .expectErrorMatches(throwable -> throwable instanceof WebClientResponseException.ServiceUnavailable)
                .verify();
    }
}