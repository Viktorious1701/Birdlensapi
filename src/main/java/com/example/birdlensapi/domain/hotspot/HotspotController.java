package com.example.birdlensapi.domain.hotspot;

import com.example.birdlensapi.common.dto.ApiResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/hotspots")
@Validated // Required to enable constraint validation on @RequestParam parameters
public class HotspotController {

    private final HotspotService hotspotService;

    public HotspotController(HotspotService hotspotService) {
        this.hotspotService = hotspotService;
    }

    @GetMapping("/nearby")
    public ResponseEntity<ApiResponse<List<EbirdNearbyHotspot>>> getNearbyHotspots(
            @RequestParam(name = "lat") @Min(-90) @Max(90) double lat,
            @RequestParam(name = "lng") @Min(-180) @Max(180) double lng,
            @RequestParam(name = "radiusKm", defaultValue = "50") @Positive double radiusKm) {

        List<EbirdNearbyHotspot> hotspots = hotspotService.getNearbyHotspots(lat, lng, radiusKm);
        return ResponseEntity.ok(ApiResponse.success(hotspots));
    }
}