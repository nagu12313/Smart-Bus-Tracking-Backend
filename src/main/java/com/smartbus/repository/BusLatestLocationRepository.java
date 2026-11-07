package com.smartbus.repository;

import com.smartbus.entity.BusLatestLocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BusLatestLocationRepository extends JpaRepository<BusLatestLocation, Long> {
    Optional<BusLatestLocation> findByBusId(Long busId);

    @Query("SELECT bll FROM BusLatestLocation bll JOIN FETCH bll.bus b LEFT JOIN FETCH b.driver LEFT JOIN FETCH b.route")
    List<BusLatestLocation> findAllWithBusDetails();
}
