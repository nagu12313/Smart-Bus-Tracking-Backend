package com.smartbus.repository;

import com.smartbus.entity.Attendance;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AttendanceRepository extends JpaRepository<Attendance, Long> {
    Optional<Attendance> findByStudentIdAndTripIdAndType(Long studentId, Long tripId, String type);
    boolean existsByStudentIdAndTripIdAndType(Long studentId, Long tripId, String type);
    Page<Attendance> findByStudentId(Long studentId, Pageable pageable);
    Page<Attendance> findByTripId(Long tripId, Pageable pageable);
}
