package com.ai.learningdetection.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class HealthDTO {
    private String status;
    private Map<String, String> subsystem;
    private Map<String, Boolean> features;
}
