package com.smartbus.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * BusLocation — GPS location reading from physical GPS device or driver mobile tracker.
 */
@Entity
@Table(name = "bus_locations", indexes = {
    @Index(name = "idx_bl_bus",     columnList = "bus_id"),
    @Index(name = "idx_bl_gpstime", columnList = "gps_time"),
    @Index(name = "idx_bl_trip",    columnList = "trip_id")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class BusLocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bus_id", nullable = false)
    private Bus bus;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id")
    private Trip trip;

    @Column(nullable = false)
    private Double latitude;

    @Column(nullable = false)
    private Double longitude;

    /** Speed in km/h */
    @Column
    @Builder.Default
    private Double speed = 0.0;

    /** Compass heading in degrees (0-360) */
    @Column
    private Double heading;

    /** GPS Accuracy radius in meters */
    @Column
    @Builder.Default
    private Double accuracy = 5.0;

    @Column(name = "snapped_latitude")
    private Double snappedLatitude;

    @Column(name = "snapped_longitude")
    private Double snappedLongitude;

    @Column(name = "location_name")
    private String locationName;

    @Column(name = "nearby_landmark")
    private String nearbyLandmark;

    /** Timestamp from the GPS device itself */
    @Column(name = "gps_time", nullable = false)
    private LocalDateTime gpsTime;

    /** Time we received and stored the record */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
