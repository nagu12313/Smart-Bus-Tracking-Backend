package com.smartbus.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * GpsDevice entity — represents a physical GPS hardware tracker installed in a bus.
 * Examples: Teltonika FMC130, Coban GPS103, SinoTrack.
 */
@Entity
@Table(name = "gps_devices", indexes = {
    @Index(name = "idx_dev_identifier", columnList = "device_identifier"),
    @Index(name = "idx_dev_status",     columnList = "device_status"),
    @Index(name = "idx_dev_last_seen",  columnList = "last_seen_at")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class GpsDevice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_identifier", nullable = false, unique = true, length = 100)
    private String deviceIdentifier;

    @Column(name = "device_name", nullable = false, length = 150)
    private String deviceName;

    @Column(name = "device_token", nullable = false, length = 255)
    private String deviceToken;

    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "bus_id", unique = true)
    private Bus bus;

    @Column(name = "device_status", nullable = false, length = 30)
    @Builder.Default
    private String deviceStatus = "ACTIVE"; // ACTIVE, STALE, OFFLINE, MAINTENANCE

    @Column(name = "firmware_version", length = 50)
    private String firmwareVersion;

    @Column(name = "sim_card_number", length = 30)
    private String simCardNumber;

    @Column(name = "battery_level")
    @Builder.Default
    private Double batteryLevel = 100.0;

    @Column(name = "last_seen_at")
    private LocalDateTime lastSeenAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
