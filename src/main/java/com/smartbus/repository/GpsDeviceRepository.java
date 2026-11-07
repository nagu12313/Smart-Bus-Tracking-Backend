package com.smartbus.repository;

import com.smartbus.entity.GpsDevice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface GpsDeviceRepository extends JpaRepository<GpsDevice, Long> {
    Optional<GpsDevice> findByDeviceIdentifier(String deviceIdentifier);
    Optional<GpsDevice> findByDeviceToken(String deviceToken);
    Optional<GpsDevice> findByBusId(Long busId);
    boolean existsByDeviceIdentifier(String deviceIdentifier);
}
