package com.smartbus.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Student entity — student profile linked to User account.
 * Also linked to their assigned bus and pickup/drop stops.
 */
@Entity
@Table(name = "students", indexes = {
    @Index(name = "idx_students_bus", columnList = "bus_id")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Student {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "roll_number", nullable = false, unique = true, length = 30)
    private String rollNumber;

    @Column(name = "class_name", nullable = false, length = 60)
    private String className;

    @Column(length = 10)
    private String section;

    @Column(name = "parent_name", length = 100)
    private String parentName;

    @Column(name = "parent_phone", length = 15)
    private String parentPhone;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "bus_id")
    private Bus bus;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "pickup_stop_id")
    private Stop pickupStop;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "drop_stop_id")
    private Stop dropStop;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
