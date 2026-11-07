package com.smartbus.repository;

import com.smartbus.entity.SupportAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SupportAttachmentRepository extends JpaRepository<SupportAttachment, Long> {

    List<SupportAttachment> findByTicketId(Long ticketId);

    List<SupportAttachment> findByMessageId(Long messageId);
}
