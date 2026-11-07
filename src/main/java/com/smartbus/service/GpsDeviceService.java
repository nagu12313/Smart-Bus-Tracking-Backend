package com.smartbus.service;

import com.smartbus.dto.request.GpsDeviceRegisterRequest;
import com.smartbus.dto.response.GpsDeviceResponse;
import com.smartbus.entity.Bus;
import com.smartbus.entity.GpsDevice;
import com.smartbus.exception.BadRequestException;
import com.smartbus.exception.ResourceNotFoundException;
import com.smartbus.repository.BusRepository;
import com.smartbus.repository.GpsDeviceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * GpsDeviceService — Administration service for registering and managing physical GPS trackers.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GpsDeviceService {

    private final GpsDeviceRepository gpsDeviceRepository;
    private final BusRepository busRepository;

    @Transactional
    public GpsDeviceResponse registerDevice(GpsDeviceRegisterRequest request) {
        if (gpsDeviceRepository.existsByDeviceIdentifier(request.getDeviceIdentifier())) {
            throw new BadRequestException("GPS device identifier already registered: " + request.getDeviceIdentifier());
        }

        Bus bus = null;
        if (request.getBusId() != null) {
            bus = busRepository.findById(request.getBusId())
                    .orElseThrow(() -> new ResourceNotFoundException("Bus", request.getBusId()));

            // Check if bus already has a registered tracker
            gpsDeviceRepository.findByBusId(bus.getId()).ifPresent(d -> {
                throw new BadRequestException("Bus " + request.getBusId() + " already has device " + d.getDeviceIdentifier() + " assigned.");
            });
        }

        String token = (request.getDeviceToken() != null && !request.getDeviceToken().isBlank())
                ? request.getDeviceToken()
                : "dev_tok_" + UUID.randomUUID().toString().replace("-", "");

        GpsDevice device = GpsDevice.builder()
                .deviceIdentifier(request.getDeviceIdentifier().trim())
                .deviceName(request.getDeviceName().trim())
                .deviceToken(token)
                .bus(bus)
                .deviceStatus("ACTIVE")
                .firmwareVersion(request.getFirmwareVersion())
                .simCardNumber(request.getSimCardNumber())
                .batteryLevel(100.0)
                .build();

        device = gpsDeviceRepository.save(device);

        // Keep bus in sync
        if (bus != null) {
            bus.setGpsDeviceId(device.getDeviceIdentifier());
            bus.setGpsDeviceToken(device.getDeviceToken());
            busRepository.save(bus);
        }

        log.info("Registered new GPS hardware device: {} for busId: {}", device.getDeviceIdentifier(), request.getBusId());
        return toResponse(device);
    }

    @Transactional(readOnly = true)
    public List<GpsDeviceResponse> getAllDevices() {
        return gpsDeviceRepository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public GpsDeviceResponse getDeviceByIdentifier(String deviceIdentifier) {
        GpsDevice device = gpsDeviceRepository.findByDeviceIdentifier(deviceIdentifier)
                .orElseThrow(() -> new ResourceNotFoundException("GpsDevice not found with identifier: " + deviceIdentifier));
        return toResponse(device);
    }

    private GpsDeviceResponse toResponse(GpsDevice device) {
        return GpsDeviceResponse.builder()
                .id(device.getId())
                .deviceIdentifier(device.getDeviceIdentifier())
                .deviceName(device.getDeviceName())
                .busId(device.getBus() != null ? device.getBus().getId() : null)
                .busNumber(device.getBus() != null ? device.getBus().getBusNumber() : "Unassigned")
                .deviceStatus(device.getDeviceStatus())
                .firmwareVersion(device.getFirmwareVersion())
                .simCardNumber(device.getSimCardNumber())
                .batteryLevel(device.getBatteryLevel())
                .lastSeenAt(device.getLastSeenAt())
                .createdAt(device.getCreatedAt())
                .build();
    }
}
