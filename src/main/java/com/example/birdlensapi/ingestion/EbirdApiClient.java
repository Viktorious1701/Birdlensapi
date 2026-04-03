package com.example.birdlensapi.ingestion;

import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
public class EbirdApiClient {

    private final WebClient ebirdWebClient;

    public EbirdApiClient(WebClient ebirdWebClient) {
        this.ebirdWebClient = ebirdWebClient;
    }

    @Retry(name = "ebirdClient")
    public Mono<List<TaxonomyDto>> fetchTaxonomy() {
        return ebirdWebClient.get()
                .uri("ref/taxonomy/ebird?fmt=json")
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<TaxonomyDto>>() {});
    }
}