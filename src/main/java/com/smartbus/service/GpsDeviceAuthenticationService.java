package com.smartbus.service;

import com.smartbus.entity.GpsDevice;
import com.smartbus.exception.BadRequestException;
import com.smartbus.repository.GpsDeviceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * GpsDeviceAuthenticationService — Validates and authenticates physical GPS tracking devices.
 * Ensures only authorized hardware installed inside buses can feed coordinates.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GpsDeviceAuthenticationService {

    private final GpsDeviceRepository gpsDeviceRepository;

    /**
     * Authenticates a GPS hardware device using its identifier and pre-shared secret token.
     * Updates lastSeenAt timestamp on successful authentication.
     */
    @Transactional
    public GpsDevice authenticateDevice(String deviceIdentifier, String deviceToken) {
        if (deviceIdentifier == null || deviceIdentifier.isBlank()) {
            throw new BadRequestException("Device identifier is required in GPS payload");
        }
        if (deviceToken == null || deviceToken.isBlank()) {
            throw new BadRequestException("GPS hardware authentication failed: Missing X-Device-Token header");
        }

        GpsDevice device = gpsDeviceRepository.findByDeviceIdentifier(deviceIdentifier)
                .orElseThrow(() -> new BadRequestException(
                        "Unknown or unregistered GPS hardware device: " + deviceIdentifier));

        if (!deviceToken.equals(device.getDeviceToken())) {
            log.warn("GPS device authentication failed for {}: Invalid token presented", deviceIdentifier);
            throw new BadRequestException("GPS hardware authentication failed: Invalid device token");
        }

        if ("DEACTIVATED".equalsIgnoreCase(device.getDeviceStatus()) ||
            "MAINTENANCE".equalsIgnoreCase(device.getDeviceStatus())) {
            throw new BadRequestException(
                    "GPS device " + deviceIdentifier + " is currently " + device.getDeviceStatus() + " and cannot ingest data.");
        }

        // Update device heartbeat
        device.setLastSeenAt(LocalDateTime.now());
        if ("OFFLINE".equalsIgnoreCase(device.getDeviceStatus()) || "STALE".equalsIgnoreCase(device.getDeviceStatus())) {
            device.setDeviceStatus("ACTIVE");
        }
        gpsDeviceRepository.save(device);

        return device;
    }
}
