package com.example.birdlensapi.domain.tour;

import com.example.birdlensapi.common.dto.ApiResponse;
import com.example.birdlensapi.domain.tour.dto.EventPageResponse;
import com.example.birdlensapi.domain.tour.dto.TourPageResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class TourController {

    private final TourService tourService;

    public TourController(TourService tourService) {
        this.tourService = tourService;
    }

    @GetMapping("/events")
    public ResponseEntity<ApiResponse<EventPageResponse>> getEvents(
            @PageableDefault(size = 20, sort = "startDate", direction = Sort.Direction.ASC) Pageable pageable) {

        EventPageResponse response = tourService.getEvents(pageable);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/tours")
    public ResponseEntity<ApiResponse<TourPageResponse>> getTours(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        TourPageResponse response = tourService.getTours(pageable);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}