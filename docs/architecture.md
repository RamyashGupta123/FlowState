# Architecture Overview

FlowState uses a microservices architecture tailored for privacy and local-first data processing.

## Components

1. **Linux Agent**
   - Python-based daemon running on the user's local machine.
   - Collects window and idle activity events.
   - Buffers events in local SQLite.
   - Syncs to backend via HTTP when possible.
2. **Browser Extension**
   - Manifest V3 Chrome extension.
   - Monitors active tab domain.
   - Respects a privacy blocklist and domain extraction.
3. **Spring Boot Backend**
   - Core API server for authentication, devices, events, and goals.
   - Java 17, Spring Boot 3, PostgreSQL via Docker.
4. **ML Service**
   - Python FastAPI sidecar.
   - Handles feature engineering and model prediction.
   - Evaluates random forest model against incoming events.

## Data Flow
- Agent/Extension -> [POST /api/events] -> Backend -> [POST /classify] -> ML Service -> Backend -> DB
