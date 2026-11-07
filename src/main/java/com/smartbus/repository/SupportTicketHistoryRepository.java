package com.smartbus.repository;

import com.smartbus.entity.SupportTicketHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SupportTicketHistoryRepository extends JpaRepository<SupportTicketHistory, Long> {

    List<SupportTicketHistory> findByTicketIdOrderByCreatedAtDesc(Long ticketId);
}
