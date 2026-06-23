package com.apiforge.service;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
@Slf4j
public class ScraperService {

    public String scrapeContent(String url) {
        log.info("Starting to scrape content from URL: {}", url);
        
        try {
            Document document = Jsoup.connect(url)
                    .timeout(10000)
                    .userAgent("Mozilla/5.0")
                    .get();
            
            StringBuilder content = new StringBuilder();
            
            // Extract paragraphs
            Elements paragraphs = document.select("p");
            for (Element paragraph : paragraphs) {
                content.append(paragraph.text()).append(" ");
            }
            
            // Extract headings h1-h4
            Elements headings = document.select("h1, h2, h3, h4");
            for (Element heading : headings) {
                content.append(heading.text()).append(" ");
            }
            
            // Extract code blocks
            Elements codeBlocks = document.select("code");
            for (Element code : codeBlocks) {
                content.append(code.text()).append(" ");
            }
            
            // Extract pre tags
            Elements preTags = document.select("pre");
            for (Element pre : preTags) {
                content.append(pre.text()).append(" ");
            }
            
            String scrapedText = content.toString().trim();
            
            // Limit to 5000 characters
            if (scrapedText.length() > 5000) {
                scrapedText = scrapedText.substring(0, 5000);
            }
            
            log.info("Successfully scraped {} characters from URL: {}", scrapedText.length(), url);
            
            return scrapedText;
            
        } catch (IOException e) {
            log.error("Failed to scrape content from URL: {}. Error: {}", url, e.getMessage());
            return "";
        }
    }
}
