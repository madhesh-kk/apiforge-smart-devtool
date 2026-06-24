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

    @Value("${claude.model:claude-3-5-haiku-latest}")
    private String claudeModel;

    private static final String CLAUDE_API_URL = "https://api.anthropic.com/v1/messages";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private final OkHttpClient client = new OkHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ApiForgeResponse extractEndpoints(String scrapedContent, String useCase) {
        log.info("Starting endpoint extraction for use case: {}", useCase);
        
        // Log API key verification (first 4 characters only for security)
        if (claudeApiKey != null && claudeApiKey.length() >= 4) {
            log.info("Claude API key loaded: {}****", claudeApiKey.substring(0, 4));
        } else {
            log.warn("Claude API key is missing or too short!");
        }

        try {
            // Log scraped content info
            log.info("Scraped content length: {}", scrapedContent.length());
            log.info("Scraped content preview: {}", scrapedContent.substring(0, Math.min(500, scrapedContent.length())));
            
            // Build the prompt for Claude
            String prompt = String.format(
                "You are an API documentation analyzer.\n" +
                "Analyze this API documentation carefully.\n\n" +
                "Use case: %s\n" +
                "Documentation: %s\n\n" +
                "You MUST respond with ONLY a valid JSON object.\n" +
                "No explanation, no markdown, no code blocks.\n" +
                "Just raw JSON exactly like this example:\n\n" +
                "{\n" +
                "  \"endpoints\": [\n" +
                "    {\n" +
                "      \"endpoint\": \"/data/2.5/weather\",\n" +
                "      \"method\": \"GET\",\n" +
                "      \"description\": \"Get current weather data\"\n" +
                "    },\n" +
                "    {\n" +
                "      \"endpoint\": \"/data/2.5/forecast\",\n" +
                "      \"method\": \"GET\",\n" +
                "      \"description\": \"Get 5 day weather forecast\"\n" +
                "    }\n" +
                "  ],\n" +
                "  \"auth_method\": \"API Key\",\n" +
                "  \"base_url\": \"https://api.openweathermap.org\"\n" +
                "}\n\n" +
                "Now analyze the documentation above and respond with JSON only. No other text.",
                useCase, scrapedContent
            );

            // Build request body in correct format
            JsonNode messagesNode = objectMapper.createObjectNode()
                    .put("model", claudeModel)
                    .put("max_tokens", 2000)
                    .set("messages", objectMapper.createArrayNode()
                            .add(objectMapper.createObjectNode()
                                    .put("role", "user")
                                    .put("content", prompt)));
            
            String requestBody = objectMapper.writeValueAsString(messagesNode);
            log.debug("Request body being sent to Claude API: {}", requestBody);

            // Build HTTP request with correct headers
            Request request = new Request.Builder()
                    .url(CLAUDE_API_URL)
                    .addHeader("x-api-key", claudeApiKey)
                    .addHeader("anthropic-version", "2023-06-01")
                    .addHeader("content-type", "application/json")
                    .post(RequestBody.create(requestBody, JSON))
                    .build();

            log.info("Calling Claude API at: {}", CLAUDE_API_URL);

            try (Response response = client.newCall(request).execute()) {
                int statusCode = response.code();
                log.info("Claude API returned status code: {}", statusCode);

                String responseBody = response.body().string();
                log.info("Full Claude API response body: {}", responseBody);

                if (!response.isSuccessful()) {
                    log.error("Claude API call failed with status: {} - Response: {}", statusCode, responseBody);
                    throw new IllegalStateException("Claude API call failed with status " + statusCode + ": " + responseBody);
                }

                // Parse Claude response
                JsonNode rootNode = objectMapper.readTree(responseBody);
                
                // Extract content array
                JsonNode contentArray = rootNode.get("content");
                
                if (contentArray == null || !contentArray.isArray() || contentArray.size() == 0) {
                    log.error("Invalid response structure from Claude API. Response: {}", responseBody);
                    throw new IllegalStateException("Invalid response structure from Claude API");
                }

                // Extract text from content[0].text
                JsonNode firstContent = contentArray.get(0);
                if (firstContent.get("type") == null || !firstContent.get("type").asText().equals("text")) {
                    log.error("Expected 'type' to be 'text', but got: {}", firstContent.get("type"));
                }
                
                String textContent = firstContent.get("text").asText();
                
                // Debug: Log Claude's raw response
                log.info("=== CLAUDE RAW RESPONSE ===");
                log.info(textContent);
                log.info("=== END CLAUDE RESPONSE ===");
                
                log.info("Extracted text content from Claude response: {}", textContent);
                
                // Extract JSON from the text content
                String jsonContent = extractJson(textContent);
                log.debug("Extracted JSON content: {}", jsonContent);
                
                // Parse the extracted JSON
                JsonNode apiDataNode;
                try {
                    apiDataNode = objectMapper.readTree(jsonContent);
                } catch (Exception jsonEx) {
                    log.error("Failed to parse JSON from Claude response. JSON content: {}", jsonContent);
                    log.error("JSON parsing error: {}", jsonEx.getMessage(), jsonEx);
                    throw new IllegalStateException("Failed to parse JSON from Claude response: " + jsonEx.getMessage(), jsonEx);
                }
                
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
                        log.debug("Parsed endpoint: {} {} - {}", 
                                endpointInfo.getMethod(), 
                                endpointInfo.getEndpoint(), 
                                endpointInfo.getDescription());
                    }
                }
                apiForgeResponse.setEndpoints(endpoints);
                
                // Parse other fields
                if (apiDataNode.has("auth_method")) {
                    String authMethod = apiDataNode.get("auth_method").asText();
                    apiForgeResponse.setAuthMethod(authMethod);
                    log.debug("Parsed auth_method: {}", authMethod);
                }
                if (apiDataNode.has("base_url")) {
                    String baseUrl = apiDataNode.get("base_url").asText();
                    apiForgeResponse.setBaseUrl(baseUrl);
                    log.debug("Parsed base_url: {}", baseUrl);
                }

                log.info("Successfully extracted {} endpoints", endpoints.size());
                log.info("Final endpoints count: {}", apiForgeResponse.getEndpoints().size());
                return apiForgeResponse;
            }

        } catch (IOException e) {
            log.error("IOException while calling Claude API: {}", e.getMessage(), e);
            throw new IllegalStateException("Error calling Claude API: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Unexpected error during endpoint extraction: {}", e.getMessage(), e);
            log.error("Stack trace: ", e);
            throw new IllegalStateException("Error extracting endpoints: " + e.getMessage(), e);
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
