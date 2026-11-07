package com.smartbus.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SupportAttachmentResponse {
    private Long id;
    private String fileName;
    private String fileType;
    private Long fileSize;
    private String fileData;
    private LocalDateTime createdAt;
}
