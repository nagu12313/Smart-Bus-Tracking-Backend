package com.smartbus.service.impl;

import com.smartbus.dto.request.DriverRequest;
import com.smartbus.dto.response.DriverResponse;
import com.smartbus.entity.Driver;
import com.smartbus.entity.Role;
import com.smartbus.entity.User;
import com.smartbus.exception.DuplicateResourceException;
import com.smartbus.exception.ResourceNotFoundException;
import com.smartbus.repository.DriverRepository;
import com.smartbus.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DriverService {

    private final DriverRepository driverRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    private volatile List<DriverResponse> cachedDrivers = null;
    private volatile long lastCacheTime = 0;
    private static final long CACHE_TTL_MS = 60_000;

    @Transactional(readOnly = true)
    public List<DriverResponse> getAllDrivers() {
        long now = System.currentTimeMillis();
        if (cachedDrivers != null && (now - lastCacheTime < CACHE_TTL_MS)) {
            return cachedDrivers;
        }
        List<DriverResponse> fresh = driverRepository.findAllWithUser().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
        this.cachedDrivers = fresh;
        this.lastCacheTime = now;
        return fresh;
    }

    public void invalidateCache() {
        this.cachedDrivers = null;
        this.lastCacheTime = 0;
    }

    @Transactional(readOnly = true)
    public Page<DriverResponse> searchDrivers(String name, Pageable pageable) {
        return driverRepository.findByUserNameContainingIgnoreCase(name, pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public DriverResponse getDriverById(Long id) {
        return toResponse(findOrThrow(id));
    }

    @Transactional
    public DriverResponse createDriver(DriverRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException("User", "email", request.getEmail());
        }
        if (driverRepository.existsByLicenseNumber(request.getLicenseNumber())) {
            throw new DuplicateResourceException("Driver", "licenseNumber", request.getLicenseNumber());
        }

        // Create user account
        String rawPassword = request.getPassword() != null ? request.getPassword() : "driver123";
        User user = User.builder()
                .name(request.getName())
                .email(request.getEmail())
                .password(passwordEncoder.encode(rawPassword))
                .role(Role.DRIVER)
                .isActive(true)
                .build();
        userRepository.save(user);

        Driver driver = Driver.builder()
                .user(user)
                .licenseNumber(request.getLicenseNumber())
                .phone(request.getPhone())
                .address(request.getAddress())
                .status(request.getStatus() != null ? request.getStatus() : "ACTIVE")
                .build();

        Driver saved = driverRepository.save(driver);
        invalidateCache();
        log.info("Driver created: {} ({})", user.getEmail(), saved.getLicenseNumber());
        return toResponse(saved);
    }

    @Transactional
    public DriverResponse updateDriver(Long id, DriverRequest request) {
        Driver driver = findOrThrow(id);
        User user = driver.getUser();

        // License update — check for duplicates
        if (!driver.getLicenseNumber().equals(request.getLicenseNumber())
                && driverRepository.existsByLicenseNumber(request.getLicenseNumber())) {
            throw new DuplicateResourceException("Driver", "licenseNumber", request.getLicenseNumber());
        }

        user.setName(request.getName());
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            user.setPassword(passwordEncoder.encode(request.getPassword()));
        }
        userRepository.save(user);

        driver.setLicenseNumber(request.getLicenseNumber());
        driver.setPhone(request.getPhone());
        if (request.getAddress() != null) driver.setAddress(request.getAddress());
        if (request.getStatus() != null) driver.setStatus(request.getStatus());

        invalidateCache();
        return toResponse(driverRepository.save(driver));
    }

    @Transactional
    public void deleteDriver(Long id) {
        Driver driver = findOrThrow(id);
        User user = driver.getUser();
        driverRepository.delete(driver);
        if (user != null) {
            userRepository.delete(user);
        }
        invalidateCache();
        log.info("Driver and associated user deleted: id={}", id);
    }

    private Driver findOrThrow(Long id) {
        return driverRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Driver", id));
    }

    public DriverResponse toResponse(Driver driver) {
        return DriverResponse.builder()
                .id(driver.getId())
                .userId(driver.getUser().getId())
                .name(driver.getUser().getName())
                .email(driver.getUser().getEmail())
                .phone(driver.getPhone())
                .licenseNumber(driver.getLicenseNumber())
                .address(driver.getAddress())
                .status(driver.getStatus())
                .createdAt(driver.getCreatedAt())
                .updatedAt(driver.getUpdatedAt())
                .build();
    }
}
