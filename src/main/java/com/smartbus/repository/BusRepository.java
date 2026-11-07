package com.smartbus.repository;

import com.smartbus.entity.Bus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BusRepository extends JpaRepository<Bus, Long> {
    Optional<Bus> findByBusNumber(String busNumber);
    boolean existsByBusNumber(String busNumber);
    List<Bus> findByStatus(String status);
    long countByStatus(String status);
    Page<Bus> findByBusNameContainingIgnoreCaseOrBusNumberContainingIgnoreCase(
        String name, String number, Pageable pageable);

    @Query("SELECT b FROM Bus b WHERE b.gpsDeviceId = :deviceId")
    Optional<Bus> findByGpsDeviceId(String deviceId);

    List<Bus> findByDriverId(Long driverId);

    /** High-speed batch retrieval with driver, user, and route joined in a single SQL query */
    @Query("SELECT DISTINCT b FROM Bus b LEFT JOIN FETCH b.driver d LEFT JOIN FETCH d.user LEFT JOIN FETCH b.route")
    List<Bus> findAllWithDetails();
}
