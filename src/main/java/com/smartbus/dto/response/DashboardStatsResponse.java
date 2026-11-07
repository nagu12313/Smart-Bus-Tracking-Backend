package com.smartbus.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class DashboardStatsResponse {
    private long totalBuses;
    private long totalDrivers;
    private long totalStudents;
    private long activeTrips;
    private long totalRoutes;
    private long activeDrivers;
    private long studentsAssigned;
    private long busesOnRoute;
}
