package com.smartbus.controller;

import com.smartbus.dto.response.ApiResponse;
import com.smartbus.dto.response.LocationSearchResultDto;
import com.smartbus.service.impl.LocationSearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping("/api/locations")
@RequiredArgsConstructor
@Tag(name = "Location Search", description = "Dynamic India-wide universal geocoding search")
public class LocationController {

    private final LocationSearchService locationSearchService;

    @GetMapping("/search")
    @Operation(summary = "Search any real-world location across India dynamically")
    public ResponseEntity<ApiResponse<List<LocationSearchResultDto>>> search(
            @RequestParam(name = "q", required = false) String query) {
        if (query == null || query.trim().length() < 2) {
            return ResponseEntity.ok(ApiResponse.success(Collections.emptyList()));
        }

        List<LocationSearchResultDto> results = locationSearchService.searchLocations(query);
        return ResponseEntity.ok(ApiResponse.success(results));
    }
}
