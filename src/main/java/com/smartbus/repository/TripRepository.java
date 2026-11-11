package com.smartbus.repository;

import com.smartbus.entity.Trip;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TripRepository extends JpaRepository<Trip, Long> {
    List<Trip> findByStatus(String status);

    @Query("SELECT t FROM Trip t JOIN FETCH t.bus JOIN FETCH t.driver d JOIN FETCH d.user JOIN FETCH t.route WHERE t.status = :status")
    List<Trip> findByStatusWithDetails(@Param("status") String status);

    long countByStatus(String status);
    Optional<Trip> findTopByBusIdAndStatusOrderByCreatedAtDesc(Long busId, String status);
    Page<Trip> findByBusId(Long busId, Pageable pageable);
    Page<Trip> findByDriverId(Long driverId, Pageable pageable);
    List<Trip> findByDriverIdOrderByCreatedAtDesc(Long driverId);
    List<Trip> findByDriverIdAndStatus(Long driverId, String status);
    Optional<Trip> findTopByDriverIdAndStatusOrderByCreatedAtDesc(Long driverId, String status);
}
