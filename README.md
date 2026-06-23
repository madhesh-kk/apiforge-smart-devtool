# APIForge - Smart DevTool for API Integration

## Description
APIForge is an AI-powered developer tool that automatically analyzes 
API documentation, extracts key endpoints and authentication methods, 
and generates a ready-to-use Java wrapper class — saving developers 
hours of manual integration work.

## Features
- Scrapes any API documentation URL automatically
- Detects authentication method (Bearer Token, API Key, OAuth)
- Extracts all key endpoints using Claude AI
- Auto-generates Java wrapper class based on your use case
- Suggests official SDKs where available

## Tech Stack
| Technology | Purpose |
|------------|---------|
| Java 17 | Core language |
| Spring Boot 3.2.0 | Backend framework |
| Jsoup | API docs scraping |
| Claude AI (Anthropic) | Endpoint extraction |
| OkHttp | HTTP client for AI calls |
| Thymeleaf | Frontend UI |
| Maven | Build tool |

## Prerequisites
- Java 17 or higher
- Maven 3.6+
- Anthropic Claude API Key

## Setup & Installation

1. Clone the repository
   git clone https://github.com/madhesh-kk/apiforge-smart-devtool.git
   cd apiforge-smart-devtool

2. Add your Claude API Key in application.properties
   claude.api.key=YOUR_CLAUDE_API_KEY_HERE

3. Build the project
   mvn clean install

4. Run the application
   mvn spring-boot:run

5. Open browser at
   http://localhost:8080

## How to Use
1. Enter any API documentation URL
2. Describe your use case
3. Select your preferred language
4. Click "Generate Wrapper"
5. Copy the generated Java wrapper class

## Solution Approach
1. Jsoup scrapes the API documentation URL
2. Extracted text is sent to Claude AI
3. Claude analyzes and returns structured endpoint data
4. GeneratorService builds a ready-to-use Java wrapper class
5. User gets a copy-paste ready integration class

## Project Structure
src/
├── main/
│   ├── java/com/apiforge/
│   │   ├── controller/    # REST endpoints
│   │   ├── service/       # Business logic
│   │   ├── model/         # Data classes
│   │   └── util/          # Constants
│   └── resources/
│       └── templates/     # Frontend UI

## License
MIT License