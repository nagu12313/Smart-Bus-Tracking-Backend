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
public class TransportChatMessageResponse {
    private String id;
    private String sender; // "user" or "assistant"
    private String message;
    private String intent;
    private LocalDateTime timestamp;
}
