package com.smartbus.repository;

import com.smartbus.entity.Driver;
import com.smartbus.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DriverRepository extends JpaRepository<Driver, Long> {
    Optional<Driver> findByUser(User user);
    Optional<Driver> findByUserId(Long userId);
    Optional<Driver> findByLicenseNumber(String licenseNumber);
    boolean existsByLicenseNumber(String licenseNumber);
    long countByStatus(String status);
    Page<Driver> findByUserNameContainingIgnoreCase(String name, Pageable pageable);
    @Query("SELECT d FROM Driver d JOIN FETCH d.user")
    java.util.List<Driver> findAllWithUser();
}
