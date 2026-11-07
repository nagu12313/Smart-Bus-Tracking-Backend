package com.smartbus.repository;

import com.smartbus.entity.Student;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StudentRepository extends JpaRepository<Student, Long> {
    Optional<Student> findByUserId(Long userId);
    Optional<Student> findByRollNumber(String rollNumber);
    boolean existsByRollNumber(String rollNumber);
    List<Student> findByBusId(Long busId);
    long countByBusIdIsNotNull();
    Page<Student> findByUserNameContainingIgnoreCaseOrRollNumberContainingIgnoreCase(
        String name, String roll, Pageable pageable);

    /** Batch eager loading in 1 query */
    @Query("SELECT s FROM Student s JOIN FETCH s.user LEFT JOIN FETCH s.bus LEFT JOIN FETCH s.pickupStop LEFT JOIN FETCH s.dropStop")
    List<Student> findAllWithDetails();
}
