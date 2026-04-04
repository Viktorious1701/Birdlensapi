package com.example.birdlensapi.domain.taxonomy;

import com.example.birdlensapi.common.dto.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/taxonomy")
public class TaxonomyController {

    private final TaxonomyService taxonomyService;

    public TaxonomyController(TaxonomyService taxonomyService) {
        this.taxonomyService = taxonomyService;
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<TaxonomySearchResponse>>> searchTaxonomy(
            @RequestParam(value = "q", defaultValue = "") String query) {

        List<TaxonomySearchResponse> results = taxonomyService.search(query);
        return ResponseEntity.ok(ApiResponse.success(results));
    }
}