package com.smartbus.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransportChatInitResponse {
    private String welcomeMessage;
    private List<String> suggestedQuestions;
    private String userRole;
    private String userName;
    private String identifiedBusNumber;
    private String identifiedBusName;
    private String contextSummary;
}
