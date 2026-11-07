package com.smartbus.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransportChatMessageRequest {

    @NotBlank(message = "Message cannot be empty")
    @Size(max = 1000, message = "Message exceeds maximum length of 1000 characters")
    private String message;
}
