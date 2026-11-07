package com.smartbus.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SupportMessageResponse {
    private Long id;
    private Long ticketId;
    private Long senderId;
    private String senderName;
    private String senderEmail;
    private String senderRole;
    private String message;
    private Boolean isAdminReply;
    private List<SupportAttachmentResponse> attachments;
    private LocalDateTime createdAt;
}
