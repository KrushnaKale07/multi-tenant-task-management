# Multi-Tenant Task Management System

A backend-only, SaaS-style task management API built with Spring Boot. Multiple organisations (tenants) share **one application and one H2 database**, while tenant ownership is enforced through JWT authentication, request filtering, service-layer checks, and tenant-scoped repository methods.

## Technology stack

- Java 17
- Spring Boot 3.3.5
- Spring Security 6
- Spring Data JPA / Hibernate
- H2 in-memory database
- JWT using JJWT 0.12.6
- BCrypt password hashing
- Jakarta Bean Validation
- Springdoc OpenAPI / Swagger UI
- JUnit 5, Mockito, Spring Boot Test and MockMvc
- JaCoCo coverage verification
- SonarQube Maven Scanner
- Docker, Render Blueprint configuration, and the current Heroku deployment

## 1. Prerequisites

Install:

- JDK 17
- Maven 3.9+
- Git
- Optional: Docker Desktop (for SonarQube or container execution)
- Optional: `jq` for the shell demo commands below

Confirm the tools are available:

```bash
java -version
mvn -version
git --version
```

## 2. Setup and run

### Clone the repository

```bash
git clone https://github.com/KrushnaKale07/multi-tenant-task-management
cd multi-tenant-task-management
```

If you have downloaded the project as a ZIP, extract it and open a terminal in the directory containing `pom.xml`.

### Configuration

Application configuration is in `src/main/resources/application.yml`; this project does not use `application.properties`.

| Environment variable | Default | Description |
|---|---|---|
| `PORT` | `8081` | HTTP port |
| `JWT_SECRET` | Development fallback in YAML | HMAC signing secret; provide a secret of at least 32 bytes in shared environments |
| `JWT_EXPIRATION_MINUTES` | `60` | JWT lifetime in minutes |
|`H2_CONSOLE_ENABLED` | `true` in the current YAML | Enables the H2 web console; set to `false` when console access is not needed |
| `H2_CONSOLE_WEB_ALLOW_OTHERS` | `false` unless configured | Allows remote H2 Console connections; the current Heroku deployment requires `true` for remote browser access. Disable after debugging/demo. |

The default database URL is `jdbc:h2:mem:taskdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE`, with username `sa` and no password. It is a single in-memory database shared by all tenants. **Data is lost when the application process stops.** This behaviour follows the assignment's H2 requirement.

For local development, the H2 console is available at `http://localhost:8081/h2-console` while enabled. Use the exact JDBC URL configured in `spring.datasource.url`; the documented default is `jdbc:h2:mem:taskdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE`, username `sa`, and an empty password unless you have configured a different credential.

### Live deployment (Heroku)

The currently shared deployment base URL is:

- **Application/API base URL:** https://enigmatic-lake-24845-1825eb87b9ea.herokuapp.com
- **H2 Console:** https://enigmatic-lake-24845-1825eb87b9ea.herokuapp.com/h2-console/
- **Swagger UI:** https://enigmatic-lake-24845-1825eb87b9ea.herokuapp.com/swagger-ui.html
- **OpenAPI JSON:** https://enigmatic-lake-24845-1825eb87b9ea.herokuapp.com/v3/api-docs
- **Health check:** https://enigmatic-lake-24845-1825eb87b9ea.herokuapp.com/actuator/health

Use the API base URL plus the endpoint path in the API tables below. For example, tenant registration is `POST https://enigmatic-lake-24845-1825eb87b9ea.herokuapp.com/tenant/register`, and login is `POST https://enigmatic-lake-24845-1825eb87b9ea.herokuapp.com/auth/login`.

For the deployed H2 Console, select the same JDBC URL used by the running application—not the H2 Console's default `jdbc:h2:~/test`. For this project's documented configuration, that URL is `jdbc:h2:mem:taskdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE`; use the deployed app's actual datasource username and password. A console connection must target the application's actual database, not a separate database named `test`.

**Security warning:** Remote H2 Console access is currently enabled to support debugging/demo access. This is an administrative database interface and should not remain publicly exposed for normal operation. Disable remote access and/or the console after the demo. Never store real customer data or production secrets in this demo database.


### Run the application

```bash
mvn spring-boot:run
```

By default, the application listens on `http://localhost:8081`.

Useful URLs:

| Purpose | URL |
|---|---|
| Swagger UI | http://localhost:8081/swagger-ui.html |
| OpenAPI JSON | http://localhost:8081/v3/api-docs |
| Health check | http://localhost:8081/actuator/health |
| H2 console (local development only) | http://localhost:8081/h2-console |

### Build a JAR

```bash
mvn clean package
java -jar target/multi-tenant-task-management-1.0.0.jar
```

## 3. API overview

All request and response bodies use JSON. Endpoints other than tenant registration, login, Swagger/OpenAPI, the error endpoint, health check, and the configured H2 console require authentication. Send the JWT in this header:

```http
Authorization: Bearer <accessToken>
```

| Method | Endpoint | Required role | Purpose |
|---|---|---|---|
| `POST` | `/tenant/register` | Public | Create a tenant and its first ADMIN user |
| `POST` | `/auth/login` | Public | Verify credentials and return a JWT |
| `POST` | `/users` | ADMIN | Create a user in the current tenant using Tenant's JWT token |
| `GET` | `/users` | ADMIN | List users in the current tenant |
| `POST` | `/auth/login` | Public | Verify credentials for MANAGER and return a JWT |
| `POST` | `/tasks` | MANAGER | Create a task in the current tenant using MANAGER's JWT token |
| `PUT` | `/tasks/{id}/assign` | MANAGER | Assign a task to a user in the same tenant using MANAGER's JWT token |
| `PUT` | `/tasks/{id}/status` | USER | Update the status of a task assigned to the current user |
| `GET` | `/tasks` | USER, MANAGER, ADMIN | List tasks visible to the current user use respective user's token |

### Request payloads

**Register a tenant and its first admin — `POST /tenant/register`**

```json
{
  "tenantName": "Acme",
  "email": "admin@acme.com",
  "password": "Passw0rd!123"
}
```

**Login — `POST /auth/login`**

```json
{
  "email": "admin@acme.com",
  "password": "Passw0rd!123"
}
```

The login response contains an `accessToken` field. Copy its value for subsequent requests.

**Create a user — `POST /users` (ADMIN)**

```json
{
  "email": "manager@acme.com",
  "password": "Passw0rd!123",
  "role": "MANAGER"
}
```

`role` must be `ADMIN`, `MANAGER`, or `USER`.

**Login — `POST /auth/login`**

```json
{
  "email": "manager@acme.com",
  "password": "Passw0rd!123"
}
```

**Create a task — `POST /tasks` (MANAGER)**

```json
{
  "title": "Prepare release",
  "description": "Prepare the v1.0 release checklist"
}
```

The task's tenant is taken from the authenticated JWT, not from the request body. New tasks start with status `TODO`.

**Assign a task — `PUT /tasks/{id}/assign` (MANAGER)**

```json
{
  "userId": 3
}
```

The assignee must belong to the same tenant as the manager and task.

**Update task status — `PUT /tasks/{id}/status` (USER)**

```json
{
  "status": "IN_PROGRESS"
}
```

Allowed statuses are `TODO`, `IN_PROGRESS`, and `DONE`. A USER can update only a task assigned to that authenticated user.

## 4. API request flow

Typical successful workflow:

1. **Register tenant:** `POST /tenant/register` creates the tenant and its first ADMIN in one transaction.
2. **Authenticate admin:** `POST /auth/login` verifies the e-mail and BCrypt password, then issues a signed JWT.
3. **Create users:** the ADMIN sends `POST /users` to create MANAGER and USER accounts in the same tenant.
4. **Authenticate manager:** the manager logs in and receives a JWT containing their user ID, tenant ID, and role.
5. **Create and assign a task:** the MANAGER calls `POST /tasks`, then `PUT /tasks/{id}/assign`.
6. **Authenticate the assignee:** the USER logs in, then calls `PUT /tasks/{id}/status`.
7. **List tasks:** `GET /tasks` returns only tasks the caller is allowed to see: assigned tasks for USER, all tenant tasks for MANAGER and ADMIN.

### Security and tenant-resolution flow

```text
HTTP request with optional Bearer token
        |
        v
TenantParameterRejectionFilter
  Rejects tenant_id / tenantId query parameters and X-Tenant-Id header
        |
        v
JwtAuthenticationFilter (OncePerRequestFilter)
  Validates JWT signature and expiry
  Sets authenticated principal and ROLE_<role> in SecurityContext
  Sets tenant_id in TenantContext for the request
        |
        v
Spring Security endpoint authentication + method-level role checks
        |
        v
Controller -> Service
  Validates role, task ownership, tenant ownership and assignment
        |
        v
Tenant-scoped repository query -> shared H2 database
        |
        v
Response; TenantContext is cleared after request processing
```

### JWT claims

The token is signed with an HMAC algorithm. Its decoded payload has this general shape (timestamps and IDs are illustrative):

```json
{
  "sub": "manager@acme.com",
  "user_id": 2,
  "tenant_id": 1,
  "role": "MANAGER",
  "iat": 1761465600,
  "exp": 1761469200
}
```

- `sub`: user e-mail
- `user_id`: authenticated user's database ID
- `tenant_id`: tenant identity used for scoped operations
- `role`: `ADMIN`, `MANAGER`, or `USER`
- `iat` / `exp`: issued-at and expiration timestamps

JWT payloads are encoded, not encrypted. Do not put secrets or passwords in claims. The signature prevents clients from modifying claims without invalidating the token.

## 5. Tenant isolation design and assumptions

Tenant isolation is enforced in multiple layers rather than relying only on controller checks:

- **JWT is the source of tenant identity.** Secured request payloads and query parameters cannot choose a tenant. DTOs do not expose a tenant ID.
- **Request filters:** `JwtAuthenticationFilter` validates the token and populates the Spring `SecurityContext` and `TenantContext`. `TenantParameterRejectionFilter` rejects explicit tenant-selection attempts via query parameters or `X-Tenant-Id`.
- **Context lifecycle:** tenant context is cleared in a `finally` block so servlet-thread reuse cannot leak one request's tenant into another.
- **Role checks:** Spring Security method annotations enforce the required roles.
- **Service-layer checks:** task ownership and assignee tenant membership are validated before updates.
- **Tenant-scoped repositories:** repository APIs expose scoped methods such as `findByIdAndTenantId` and `findAllByTenantId`, rather than unrestricted `JpaRepository.findAll()` / `findById()` methods. E-mail lookups are unscoped where needed for login and global uniqueness.
- **Persistence guard:** `TenantEntityListener` checks tenant ownership before persistence updates.
- **Not-found behaviour:** a task or user belonging to a different tenant is treated as not found, avoiding disclosure of another tenant's resource IDs.

### Explicit design decisions

1. **Single database:** all tenants share one in-memory H2 database; no tenant-specific database or schema is created.
2. **Global e-mail uniqueness:** e-mail addresses are normalized and unique across tenants because login accepts only e-mail and password and has no tenant selector.
3. **Initial administrator:** tenant registration creates the tenant and its first ADMIN atomically.
4. **Role capabilities:** ADMIN manages users and can view all tenant tasks; MANAGER creates and assigns tasks; USER views assigned tasks and updates their own assigned task status.
5. **Assignment policy:** a manager may assign a task to a user in the same tenant; reassignment is permitted.
6. **Task lifecycle:** tasks start as `TODO`; status values are `TODO`, `IN_PROGRESS`, and `DONE`.
7. **Stateless authentication:** no server-side session, refresh token, or token revocation/logout mechanism is implemented; the access token expires according to `JWT_EXPIRATION_MINUTES`.
8. **Schema management:** Hibernate uses `ddl-auto: update` in the provided YAML. This is convenient for the assignment, but a migration tool such as Flyway would be preferable for a production system.
9. **Error handling:** API errors use a consistent JSON structure. Validation and malformed input typically return `400`, unauthenticated requests `401`, role/operation denials `403`, missing or cross-tenant resources `404`, and duplicate resources `409`.

## 6. Validation and error responses

Request DTOs use Jakarta Bean Validation. Examples include required fields, valid e-mail addresses, password length of 8–72 characters, bounded task title/description lengths, positive user IDs, and valid enum values.

Example error shape:

```json
{
  "timestamp": "2026-10-26T10:15:30Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "path": "/tenant/register",
  "fieldErrors": {
    "password": "password must be 8-72 characters"
  }
}
```

`GlobalExceptionHandler` handles controller/service exceptions, while `RestSecurityErrorHandler` formats security-filter errors. Unexpected server errors should not expose internal stack traces to clients.

## 7. Run tests and check coverage

Run the complete test suite and JaCoCo verification:

```bash
mvn clean verify
```

The Maven build is configured to fail when **line coverage is below 80%**. The HTML coverage report is generated at:

```text
target/site/jacoco/index.html
```

The project contains unit tests for configuration, JWT handling, security utilities, tenant context/entity listener and task service, plus MockMvc integration tests for tenant isolation and security. Run the command above to verify the current test result and actual coverage; do not report a coverage percentage until the build completes successfully.

## 8. SonarQube code quality analysis

### Start local SonarQube

Docker Desktop must be running:

```bash
docker compose -f docker-compose.sonar.yml up -d
```

Open `http://localhost:9000`. On first login, follow the SonarQube setup flow, change the default credentials if prompted, and create a user token under **My Account → Security**.

Run tests, generate the JaCoCo report, and analyse the project:

```bash
mvn clean verify sonar:sonar -Dsonar.token=YOUR_SONAR_TOKEN
```

The scanner configuration and JaCoCo XML report path are declared in `pom.xml`. Do not commit the SonarQube token to Git. For SonarCloud, supply the appropriate host URL and organization/project settings as command-line properties or environment-based CI secrets.

## 9. Docker and deployment

### Run with Docker

Build the image:

```bash
docker build -t multi-tenant-task-management .
```

Run it with a strong JWT secret:

```bash
docker run --rm -p 8081:8081 `
  -e PORT=8081 `
  -e JWT_SECRET="replace-with-a-random-secret-at-least-32-characters" `
  -e H2_CONSOLE_ENABLED=false `
  multi-tenant-task-management
```

The supplied Dockerfile builds and runs the application on Java 17. The application's default local port is `8081`; the `PORT` environment variable can override it.
### Current Heroku deployment

The live deployment currently used for API and H2 Console demonstrations is:

`https://enigmatic-lake-24845-1825eb87b9ea.herokuapp.com`

Configure the following Heroku Config Vars only if the console is intentionally required for a temporary demo:

| Config Var | Value | Purpose |
|---|---|---|
| `H2_CONSOLE_ENABLED` | `true` | Enables the console endpoint |
| `H2_CONSOLE_WEB_ALLOW_OTHERS` | `true` | Allows remote console connections |
| `JWT_SECRET` | A strong random secret of at least 32 bytes | Signs JWTs; never commit this value |

The YAML should reference these variables, for example:

```yaml
spring:
  h2:
    console:
      enabled: ${H2_CONSOLE_ENABLED:false}
      path: /h2-console
      settings:
        web-allow-others: ${H2_CONSOLE_WEB_ALLOW_OTHERS:false}
```

After changing local files, commit and deploy them to Heroku; changing a local YAML file alone does not update the deployed service. Set Config Vars in the Heroku dashboard under **Settings → Config Vars**. If the console shows `Database "/app/test" not found`, replace the default console JDBC URL with the exact datasource URL configured by the application. Do not create a separate `test` database to work around the mismatch.

**Important:** H2's `mem:` database is in-memory. It is shared within the relevant running application process, but it is not durable and may be empty after a process restart/redeploy. Do not assume the console's connection is inspecting the application's database unless its JDBC URL and runtime configuration match.

### Render (optional alternative)


### Render

The repository includes `render.yaml` and a Dockerfile. To deploy:

1. Push the complete project to GitHub.
2. In Render, create a new Blueprint or Docker Web Service and select the repository.
3. Confirm the health check path is `/actuator/health`.
4. Configure a strong `JWT_SECRET` in the service environment and set `H2_CONSOLE_ENABLED=false` unless remote console access is explicitly needed for a short demo.
5. Deploy and verify the health endpoint and main API flow.

**Deployment limitation:** H2 is in-memory. Data is not durable and will be lost when the process restarts or the service is redeployed. This matches the assignment's single-H2 requirement but is not suitable for a production multi-tenant SaaS without a persistent database.

## 10. Repository structure

```text
src/main/java/com/techpulse/taskmanagement/
├── config/       Security, JWT properties and OpenAPI configuration
├── controller/   REST controllers
├── dto/          Validated request and response DTOs
├── entity/       Tenant, User, Task and enums
├── exception/    API error model, global handler and custom exceptions
├── repository/   Tenant-scoped repository interfaces
├── security/     JWT service/filter, authentication principal and error handling
├── service/      Authentication, tenant, user and task business logic
├── tenant/       Tenant context, ownership contract and entity listener
└── util/         E-mail normalization

src/test/java/com/techpulse/taskmanagement/
├── config/
├── integration/
├── security/
├── service/
└── tenant/
```

## 11. Before submission

- [ ] `mvn clean verify` succeeds and coverage is at least 80%.
- [ ] SonarQube analysis has been run and findings reviewed.
- [ ] README instructions have been followed from a clean checkout.
- [ ] No real secrets, access tokens, or passwords are committed.
- [ ] GitHub repository contains the complete source.
