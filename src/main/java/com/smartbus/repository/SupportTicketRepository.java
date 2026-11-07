package com.smartbus.repository;

import com.smartbus.entity.SupportTicket;
import com.smartbus.entity.TicketCategory;
import com.smartbus.entity.TicketPriority;
import com.smartbus.entity.TicketStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SupportTicketRepository extends JpaRepository<SupportTicket, Long> {

    Optional<SupportTicket> findByTicketNumber(String ticketNumber);

    Page<SupportTicket> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    @Query("SELECT t FROM SupportTicket t LEFT JOIN FETCH t.user LEFT JOIN FETCH t.bus LEFT JOIN FETCH t.assignedTo WHERE t.user.id = :userId ORDER BY t.createdAt DESC")
    List<SupportTicket> findByUserIdOrderByCreatedAtDesc(@Param("userId") Long userId);

    @Query(value = "SELECT t FROM SupportTicket t LEFT JOIN FETCH t.user LEFT JOIN FETCH t.bus LEFT JOIN FETCH t.assignedTo WHERE " +
           "(:status IS NULL OR t.status = :status) AND " +
           "(:priority IS NULL OR t.priority = :priority) AND " +
           "(:category IS NULL OR t.category = :category) AND " +
           "(:search IS NULL OR LOWER(t.subject) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(t.ticketNumber) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(t.description) LIKE LOWER(CONCAT('%', :search, '%'))) " +
           "ORDER BY t.createdAt DESC",
           countQuery = "SELECT COUNT(t) FROM SupportTicket t WHERE " +
           "(:status IS NULL OR t.status = :status) AND " +
           "(:priority IS NULL OR t.priority = :priority) AND " +
           "(:category IS NULL OR t.category = :category) AND " +
           "(:search IS NULL OR LOWER(t.subject) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(t.ticketNumber) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(t.description) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<SupportTicket> findWithFilters(
            @Param("status") TicketStatus status,
            @Param("priority") TicketPriority priority,
            @Param("category") TicketCategory category,
            @Param("search") String search,
            Pageable pageable
    );

    long countByStatus(TicketStatus status);
}
