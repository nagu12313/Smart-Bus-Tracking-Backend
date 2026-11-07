package com.smartbus.repository;

import com.smartbus.entity.BusLocationHistory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface BusLocationHistoryRepository extends JpaRepository<BusLocationHistory, Long> {
    @Query("SELECT blh FROM BusLocationHistory blh WHERE blh.bus.id = :busId ORDER BY blh.gpsTimestamp DESC")
    List<BusLocationHistory> findLatestHistoryByBusId(@Param("busId") Long busId, Pageable pageable);

    List<BusLocationHistory> findByBusIdAndGpsTimestampBetweenOrderByGpsTimestampAsc(
            Long busId, LocalDateTime start, LocalDateTime end);

    List<BusLocationHistory> findByTripIdOrderByGpsTimestampAsc(Long tripId);
}
