package com.smartbus.repository;

import com.smartbus.entity.BusRouteAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BusRouteAssignmentRepository extends JpaRepository<BusRouteAssignment, Long> {
    List<BusRouteAssignment> findByBusId(Long busId);
    List<BusRouteAssignment> findByRouteId(Long routeId);
    List<BusRouteAssignment> findByDriverId(Long driverId);
    List<BusRouteAssignment> findByIsActiveTrue();
}
