package com.smartbus.service.impl;

import com.smartbus.dto.response.DashboardStatsResponse;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DashboardService {

    @PersistenceContext
    private final EntityManager entityManager;

    private volatile DashboardStatsResponse cachedStats = null;
    private volatile long lastFetchTime = 0;
    private static final long CACHE_TTL_MS = 30_000; // 30 seconds high-speed cache

    @Transactional(readOnly = true)
    public DashboardStatsResponse getStats() {
        long now = System.currentTimeMillis();
        if (cachedStats != null && (now - lastFetchTime) < CACHE_TTL_MS) {
            return cachedStats;
        }

        // Single network round-trip for all 8 counts instead of 8 separate queries
        String sql = """
            SELECT
                (SELECT COUNT(*) FROM buses),
                (SELECT COUNT(*) FROM drivers),
                (SELECT COUNT(*) FROM students),
                (SELECT COUNT(*) FROM trips WHERE status = 'IN_PROGRESS'),
                (SELECT COUNT(*) FROM routes),
                (SELECT COUNT(*) FROM drivers WHERE status = 'ACTIVE'),
                (SELECT COUNT(*) FROM students WHERE bus_id IS NOT NULL),
                (SELECT COUNT(*) FROM buses WHERE status = 'ON_ROUTE')
            """;

        Object[] result = (Object[]) entityManager.createNativeQuery(sql).getSingleResult();

        DashboardStatsResponse fresh = DashboardStatsResponse.builder()
                .totalBuses(((Number) result[0]).longValue())
                .totalDrivers(((Number) result[1]).longValue())
                .totalStudents(((Number) result[2]).longValue())
                .activeTrips(((Number) result[3]).longValue())
                .totalRoutes(((Number) result[4]).longValue())
                .activeDrivers(((Number) result[5]).longValue())
                .studentsAssigned(((Number) result[6]).longValue())
                .busesOnRoute(((Number) result[7]).longValue())
                .build();

        this.cachedStats = fresh;
        this.lastFetchTime = now;
        return fresh;
    }
}
