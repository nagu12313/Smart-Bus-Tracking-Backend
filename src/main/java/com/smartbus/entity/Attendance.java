package com.smartbus.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Attendance — records student pickup or drop-off during a trip.
 * Type: PICKUP or DROPOFF
 * Unique per (student, trip, type) to prevent duplicates.
 */
@Entity
@Table(name = "attendance",
       uniqueConstraints = @UniqueConstraint(columnNames = {"student_id","trip_id","type"}),
       indexes = {
           @Index(name = "idx_att_student", columnList = "student_id"),
           @Index(name = "idx_att_trip",    columnList = "trip_id"),
           @Index(name = "idx_att_date",    columnList = "recorded_at")
       })
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Attendance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "trip_id", nullable = false)
    private Trip trip;

    /** PICKUP or DROPOFF */
    @Column(nullable = false, length = 10)
    private String type;

    @CreationTimestamp
    @Column(name = "recorded_at", nullable = false, updatable = false)
    private LocalDateTime recordedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recorded_by")
    private User recordedBy;

    @Column(length = 255)
    private String notes;
}
