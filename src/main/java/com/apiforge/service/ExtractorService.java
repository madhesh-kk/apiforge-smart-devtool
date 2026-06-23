package com.apiforge.service;

import com.apiforge.model.ApiForgeResponse;
import com.apiforge.model.EndpointInfo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class ExtractorService {

    @Value("${claude.api.key}")
    private String claudeApiKey;

    private static final String CLAUDE_API_URL = "https://api.anthropic.com/v1/messages";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private final OkHttpClient client = new OkHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ApiForgeResponse extractEndpoints(String scrapedContent, String useCase) {
        log.info("Starting endpoint extraction for use case: {}", useCase);

        try {
            String prompt = String.format(
                "You are an API documentation analyzer. Analyze this API documentation and extract information.\n" +
                "Use case: %s\n" +
                "Documentation: %s\n\n" +
                "Respond ONLY in this JSON format with no extra text:\n" +
                "{\n" +
                "  'endpoints': [\n" +
                "    {'endpoint': '/path', 'method': 'GET/POST', 'description': 'what it does'}\n" +
                "  ],\n" +
                "  'auth_method': 'Bearer Token or API Key or OAuth',\n" +
                "  'base_url': 'https://api.example.com'\n" +
                "}",
                useCase, scrapedContent
            );

            String requestBody = String.format(
                "{\n" +
                "  \"model\": \"claude-sonnet-4-6\",\n" +
                "  \"max_tokens\": 1000,\n" +
                "  \"messages\": [\n" +
                "    {\n" +
                "      \"role\": \"user\",\n" +
                "      \"content\": %s\n" +
                "    }\n" +
                "  ]\n" +
                "}",
                objectMapper.writeValueAsString(prompt)
            );

            Request request = new Request.Builder()
                    .url(CLAUDE_API_URL)
                    .addHeader("x-api-key", claudeApiKey)
                    .addHeader("anthropic-version", "2023-06-01")
                    .addHeader("Content-Type", "application/json")
                    .post(RequestBody.create(requestBody, JSON))
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    log.error("Claude API call failed with status: {}", response.code());
                    return new ApiForgeResponse();
                }

                String responseBody = response.body().string();
                log.debug("Claude API response: {}", responseBody);

                // Parse Claude response
                JsonNode rootNode = objectMapper.readTree(responseBody);
                JsonNode contentArray = rootNode.get("content");
                
                if (contentArray == null || !contentArray.isArray() || contentArray.size() == 0) {
                    log.error("Invalid response structure from Claude API");
                    return new ApiForgeResponse();
                }

                String textContent = contentArray.get(0).get("text").asText();
                
                // Extract JSON from the text content
                String jsonContent = extractJson(textContent);
                
                // Parse the extracted JSON
                JsonNode apiDataNode = objectMapper.readTree(jsonContent);
                
                // Map to ApiForgeResponse
                ApiForgeResponse apiForgeResponse = new ApiForgeResponse();
                
                // Parse endpoints
                List<EndpointInfo> endpoints = new ArrayList<>();
                JsonNode endpointsNode = apiDataNode.get("endpoints");
                if (endpointsNode != null && endpointsNode.isArray()) {
                    for (JsonNode endpointNode : endpointsNode) {
                        EndpointInfo endpointInfo = new EndpointInfo(
                            endpointNode.get("endpoint").asText(),
                            endpointNode.get("method").asText(),
                            endpointNode.get("description").asText()
                        );
                        endpoints.add(endpointInfo);
                    }
                }
                apiForgeResponse.setEndpoints(endpoints);
                
                // Parse other fields
                if (apiDataNode.has("auth_method")) {
                    apiForgeResponse.setAuthMethod(apiDataNode.get("auth_method").asText());
                }
                if (apiDataNode.has("base_url")) {
                    apiForgeResponse.setBaseUrl(apiDataNode.get("base_url").asText());
                }

                log.info("Successfully extracted {} endpoints", endpoints.size());
                return apiForgeResponse;
            }

        } catch (IOException e) {
            log.error("Error calling Claude API: {}", e.getMessage(), e);
            return new ApiForgeResponse();
        } catch (Exception e) {
            log.error("Unexpected error during endpoint extraction: {}", e.getMessage(), e);
            return new ApiForgeResponse();
        }
    }

    private String extractJson(String text) {
        // Try to find JSON content between curly braces
        int startIndex = text.indexOf("{");
        int endIndex = text.lastIndexOf("}");
        
        if (startIndex != -1 && endIndex != -1 && endIndex > startIndex) {
            return text.substring(startIndex, endIndex + 1);
        }
        
        // If no braces found, return the original text
        return text;
    }
}
