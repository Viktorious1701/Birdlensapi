# Technical Updates and Configuration Fixes

**Date:** March 28, 2026
**Status:** Applied

This document tracks important modifications made to the project's dependencies and configuration files to resolve startup crashes and environment conflicts. Please reference this file before adding new configuration files or upgrading core Spring dependencies.

## 1. Configuration Consolidation (Deleted `application.properties`)

### The Issue
The application was crashing on startup with the error: `Driver org.postgresql.Driver claims to not accept jdbcUrl, ${DB_URL}`.

This occurred because the project accidentally contained both `src/main/resources/application.properties` and `src/main/resources/application.yml`. Spring Boot loaded both, but the `.properties` file enforced strict environment variables without local defaults (e.g., `${DB_URL}`). When running locally without Docker injecting these variables, the database connection failed.

### The Resolution
*   **Deleted** `src/main/resources/application.properties` to eliminate the conflict.
*   **Updated** `src/main/resources/application.yml` to act as the single source of truth.
*   **Migrated** the `spring.jpa.open-in-view: false` setting from the deleted properties file into the YAML file to maintain optimal database connection performance.

## 2. Dependency & Tech Version Updates

### The Issue
The application threw an `IllegalStateException` related to `org.springdoc.webmvc.ui.SwaggerConfig` during startup. This was caused by two overlapping issues:
1. Version mismatch between Spring Boot and the Springdoc OpenAPI library.
2. An ambiguous web environment. The project requires `WebClient` (from WebFlux) for external eBird API calls, but the core application is a traditional WebMVC application. Springdoc was confused about which environment to auto-configure.

### The Resolution
*   **Spring Boot Version:** Pinned to `3.3.1`.
*   **Springdoc OpenAPI Version:** Pinned to `2.5.0` (compatible with Spring Boot 3.3.1).
*   **WebFlux Scope Correction:** Moved `spring-boot-starter-webflux` from `testImplementation` to `implementation` in `build.gradle` because `WebClient` is required in the main source code for the `EbirdApiClient`.
*   **Explicit Web Type:** Added `spring.main.web-application-type: servlet` to `application.yml`. This explicitly forces Spring Boot and Springdoc to initialize as a standard Servlet (WebMVC) application, safely ignoring the reactive WebFlux auto-configuration triggers while still allowing us to use the `WebClient` utility.