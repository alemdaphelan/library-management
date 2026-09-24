# HUIT Library Management System - Backend

This is the Core Backend service for the HUIT Library Management System, built with **Spring Boot 3.x** and **Java 17**.

## 🚀 Getting Started

There are two primary ways to run this application:

### Method 1: Running with Docker Compose (Recommended)

This is the easiest way to run the entire system, as it spins up the Backend, Nginx API Gateway, PostgreSQL, MongoDB, Redis, Elasticsearch, and Kafka in containers.

1. **Prerequisites**: Ensure you have Docker and Docker Compose installed.
2. **Navigate to the root directory** (where `docker-compose.yml` is located):
   ```bash
   cd ../
   ```
3. **Start all services**:
   ```bash
   docker-compose up -d --build
   ```
4. **Access the Application**:
   - Backend APIs (via Nginx): `http://localhost/api/v1/`
   - Swagger UI Documentation: `http://localhost/api/v1/swagger-ui/index.html` (Assuming Nginx routes correctly, or directly via `http://localhost:8080/swagger-ui/index.html`)

To stop the services, run:
```bash
docker-compose down
```

---

### Method 2: Running Locally for Development

If you want to run the Spring Boot application locally (e.g., from IntelliJ IDEA or via Maven) while depending on Docker for the databases/services, follow these steps:

1. **Spin up Infrastructure Services only**:
   Run the following command from the root directory to start the databases and broker without starting the backend container:
   ```bash
   docker-compose up -d postgres mongodb redis elasticsearch zookeeper kafka
   ```

2. **Configure Environment Variables**:
   Ensure your local run configuration uses the `dev` profile. The `application.yml` is already set up to fallback to `localhost` for services if environment variables are not provided. 
   *(See `application.yml` for default values).*

3. **Run the Spring Boot App**:
   From the `backend` directory, run:
   ```bash
   mvn spring-boot:run
   ```
   Or run the `LibraryApplication.java` main class directly from your IDE.

4. **Access the Application**:
   - API Base URL: `http://localhost:8080/api/v1/`
   - Swagger UI: `http://localhost:8080/swagger-ui/index.html`

## 📚 API Documentation (Swagger)

The project uses `springdoc-openapi` to automatically generate API documentation based on OpenAPI 3.0 specifications.

Once the backend is running, you can access the Swagger UI at:
👉 **[http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html)**

**Features configured:**
- JWT Bearer Token Authentication support via the `Authorize` button.
- Auto-scanned Controllers and DTOs.

## 🛠️ Database Migrations (Liquibase)

We use **Liquibase** to manage database schema versions. 
- All changesets are located in `src/main/resources/db/changelog/changesets/`.
- The master file is `src/main/resources/db/changelog/db.changelog-master.yaml`.
- When the application starts, Liquibase will automatically validate and apply any pending schema changes to PostgreSQL.

## 🏗️ Modules Overview

- **Auth**: JWT-based authentication and user management.
- **Catalog**: Book inventory, synced with Elasticsearch for fuzzy searching.
- **Circulation**: Borrow/Return logic, fines, and waitlists.
- **Penalty**: Wallet deposits and dynamic fine calculations.
- **Facilities**: Room booking and Kiosk management.
- **Digital**: Digital documents and DRM handling.
- **Interactive**: Reviews, ratings, and complaints.
- **Notification**: Real-time STOMP over WebSocket messaging backed by Kafka.
- **AI**: Integrations with the external Python AI Recommendation service.
