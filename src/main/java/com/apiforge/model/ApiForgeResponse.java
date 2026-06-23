package com.apiforge.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiForgeResponse {
    private List<EndpointInfo> endpoints;
    private String authMethod;
    private String baseUrl;
    private String generatedCode;
    private String sdkSuggestion;
}
