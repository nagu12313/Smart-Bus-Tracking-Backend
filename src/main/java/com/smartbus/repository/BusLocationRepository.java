package com.smartbus.repository;

import com.smartbus.entity.BusLocation;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BusLocationRepository extends JpaRepository<BusLocation, Long> {

    /** Latest location for a specific bus with eager bus & driver join */
    @Query("SELECT bl FROM BusLocation bl JOIN FETCH bl.bus b LEFT JOIN FETCH b.driver d LEFT JOIN FETCH d.user LEFT JOIN FETCH bl.trip WHERE bl.bus.id = :busId ORDER BY bl.gpsTime DESC LIMIT 1")
    Optional<BusLocation> findLatestByBusId(@Param("busId") Long busId);

    /** Fallback finder */
    Optional<BusLocation> findTopByBusIdOrderByGpsTimeDesc(Long busId);

    /** Location history for a bus, most recent first */
    @Query("SELECT bl FROM BusLocation bl JOIN FETCH bl.bus LEFT JOIN FETCH bl.trip WHERE bl.bus.id = :busId ORDER BY bl.gpsTime DESC")
    List<BusLocation> findHistoryByBusId(@Param("busId") Long busId, Pageable pageable);

    /** Latest location for every bus (single query with eager Bus & Driver join) */
    @Query("SELECT bl FROM BusLocation bl JOIN FETCH bl.bus b LEFT JOIN FETCH b.driver d LEFT JOIN FETCH d.user LEFT JOIN FETCH bl.trip WHERE bl.id IN (" +
           "SELECT MAX(bl2.id) FROM BusLocation bl2 GROUP BY bl2.bus.id)")
    List<BusLocation> findLatestLocationForAllBuses();
}
