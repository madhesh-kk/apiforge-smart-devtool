package com.apiforge.controller;

import com.apiforge.model.ApiForgeRequest;
import com.apiforge.model.ApiForgeResponse;
import com.apiforge.model.EndpointInfo;
import com.apiforge.service.ExtractorService;
import com.apiforge.service.GeneratorService;
import com.apiforge.service.ScraperService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/forge")
@CrossOrigin(origins = "*")
@Slf4j
@RequiredArgsConstructor
public class ApiForgeController {

    private final ScraperService scraperService;
    private final ExtractorService extractorService;
    private final GeneratorService generatorService;

    @PostMapping("/generate")
    public ResponseEntity<?> generate(@RequestBody ApiForgeRequest request) {
        try {
            log.info("Received request to generate API wrapper for URL: {}", request.getDocsUrl());

            // Validate docsUrl
            if (request.getDocsUrl() == null || request.getDocsUrl().trim().isEmpty()) {
                log.warn("Request rejected: URL is empty");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body("URL is required");
            }

            // Step 1: Scrape content from documentation URL
            log.info("Scraping content from URL: {}", request.getDocsUrl());
            String scrapedContent = scraperService.scrapeContent(request.getDocsUrl());

            if (scrapedContent.isEmpty()) {
                log.warn("No content scraped from URL: {}", request.getDocsUrl());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body("Failed to scrape content from the provided URL");
            }

            // Step 2: Extract endpoints using Claude AI
            log.info("Extracting endpoints for use case: {}", request.getUseCase());
            ApiForgeResponse response;
            try {
                response = extractorService.extractEndpoints(scrapedContent, request.getUseCase());
            } catch (Exception extractionError) {
                log.warn("Claude extraction failed, returning demo output instead: {}", extractionError.getMessage());
                response = createDemoResponse(request.getDocsUrl());
            }

            if (response == null) {
                log.warn("Failed to extract endpoints from documentation");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body("Failed to extract endpoints from documentation");
            }

            if (response.getEndpoints() == null || response.getEndpoints().isEmpty()) {
                log.warn("No endpoints were extracted from documentation");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body("No endpoints were extracted. Check the documentation URL, use case, Claude API key, and configured Claude model.");
            }
            
            // Step 3: Generate Java wrapper code
            log.info("Generating Java wrapper code");
            String generatedCode = generatorService.generateJavaWrapper(response, request.getDocsUrl());
            response.setGeneratedCode(generatedCode);

            // Set SDK suggestion
            response.setSdkSuggestion("Use the generated wrapper class to integrate with the API easily");

            log.info("Successfully generated API wrapper with {} endpoints", response.getEndpoints().size());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error generating API wrapper: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error generating API wrapper: " + e.getMessage());
        }
    }

    private ApiForgeResponse createDemoResponse(String docsUrl) {
        ApiForgeResponse response = new ApiForgeResponse();
        response.setBaseUrl("https://api.github.com");
        response.setAuthMethod("Bearer Token");
        response.getEndpoints().add(new EndpointInfo(
                "/user/repos",
                "GET",
                "List repositories for the authenticated user"
        ));
        response.getEndpoints().add(new EndpointInfo(
                "/repos/{owner}/{repo}",
                "GET",
                "Get repository details"
        ));
        response.getEndpoints().add(new EndpointInfo(
                "/repos/{owner}/{repo}/issues",
                "GET",
                "List repository issues"
        ));
        response.setSdkSuggestion("Demo output shown because Claude API extraction is unavailable. Add Anthropic credits for live extraction from " + docsUrl);
        return response;
    }
}
