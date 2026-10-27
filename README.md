# Multi-Tenant Task Management System (Single Database)

SaaS-style task management backend: many organisations (**tenants**) share one Spring Boot application and
**one H2 database**, but their data is completely isolated. Built for the TechPulse IT assignment.

**Stack:** Java 17 · Spring Boot 3.3 · Spring Security 6 · JWT (jjwt 0.12) · Spring Data JPA · H2 · Lombok ·
Bean Validation · springdoc (Swagger UI) · JUnit 5 / Mockito / MockMvc · JaCoCo · SonarQube

---

## 1. Run the application

```bash
# prerequisites: JDK 17+, Maven 3.9+
mvn spring-boot:run          # http://localhost:8080
```

| What | URL |
|------|-----|
| Swagger UI (use the *Authorize* button with the login token) | http://localhost:8080/swagger-ui.html |
| Health | http://localhost:8080/actuator/health |
| H2 console (disabled by default) | `H2_CONSOLE_ENABLED=true` → http://localhost:8080/h2-console, JDBC URL `jdbc:h2:mem:taskdb`, user `sa`, empty password |

Configuration lives in `src/main/resources/application.yml` (no `application.properties`).

| Env variable | Default | Meaning |
|--------------|---------|---------|
| `PORT` | `8080` | HTTP port |
| `JWT_SECRET` | dev default | HMAC secret, **min 32 characters** – set it in any shared environment |
| `JWT_EXPIRATION_MINUTES` | `60` | Token lifetime |
| `H2_CONSOLE_ENABLED` | `false` | Enable H2 web console |

### Tests, coverage, Sonar

```bash
mvn clean verify                 # runs tests, writes target/site/jacoco/index.html, FAILS if line coverage < 80 %
```

SonarQube (local):

```bash
docker compose -f docker-compose.sonar.yml up -d       # http://localhost:9000  (admin / admin)
# create a token in SonarQube: My Account -> Security
mvn clean verify sonar:sonar -Dsonar.token=<token>
```

Coverage is imported from JaCoCo (`sonar.coverage.jacoco.xmlReportPaths` in `pom.xml`).
For SonarCloud add `-Dsonar.host.url=https://sonarcloud.io -Dsonar.organization=<org>`.

### Docker / deployment (Render, Railway, ...)

```bash
docker build -t task-mgmt . && docker run -p 8080:8080 -e JWT_SECRET=<32+ chars> task-mgmt
```

On **Render**: *New → Web Service → connect the GitHub repo → Runtime: Docker* (or use `render.yaml` as a Blueprint).
Set `JWT_SECRET`; health check path `/actuator/health`. Note: H2 is in-memory, so data resets on every restart
(accepted by the assignment: "single H2 database").

---

## 2. How tenant isolation is achieved (defence in depth)

| Layer | Mechanism |
|-------|-----------|
| **Token** | JWT carries `tenant_id` and `role`; it is signed (HMAC) so a client cannot alter them. |
| **Filter** | `JwtAuthenticationFilter` (`OncePerRequestFilter`) validates the token, then stores the identity in the `SecurityContext` **and** the tenant in a `ThreadLocal` (`TenantContext`). The context is always cleared in a `finally` block. |
| **Client input** | `tenant_id` is never accepted from the client: `TenantParameterRejectionFilter` rejects `tenant_id`/`tenantId` query params and `X-Tenant-Id` headers (400), and Jackson is configured with `fail-on-unknown-properties: true`, so a `tenant_id` in a JSON body is rejected (400). DTOs have no tenant field at all. |
| **Service** | Services call `TenantContext.requireTenantId()`; new rows always get the tenant from the JWT. Assigning a task validates that the assignee belongs to the same tenant. |
| **Repository** | Repositories extend the bare `Repository` marker (not `JpaRepository`), so `findAll()`/`findById()` are **not exposed**. Every method is tenant-scoped (`findByIdAndTenantId`, `findAllByTenantId…`). Ownership is thus verified on every load before any update. The only unscoped lookup is `UserRepository.findByEmail/existsByEmail`, which is needed *before* a tenant is known (login, uniqueness). |
| **Persistence** | `TenantEntityListener` (`@PrePersist/@PreUpdate`) aborts any write whose `tenant_id` differs from the current request's tenant. |
| **Database** | One H2 database; `tenant_id` column exists in every table (`tenants.tenant_id` is the PK, `users.tenant_id`, `tasks.tenant_id`, indexed). No extra schemas/databases. |
| **Responses** | A resource of another tenant is answered with **404**, not 403, so its existence is not leaked. |

## 3. JWT structure and claims

HS256/HS384/HS512 (chosen by key length) signed token, default lifetime 60 min.

Example payload (decoded):

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

| Claim | Meaning |
|-------|---------|
| `sub` | user e-mail |
| `user_id` | id of the user (used e.g. to check "assigned to me") |
| `tenant_id` | **the only source of the tenant** for the whole request |
| `role` | `ADMIN`, `MANAGER` or `USER` → mapped to authority `ROLE_<role>` |
| `iat` / `exp` | issued-at / expiry |

## 4. Security filter flow

```
HTTP request
  │
  ▼
TenantParameterRejectionFilter ── tenant_id in query/header? ──► 400 JSON error
  │
  ▼
JwtAuthenticationFilter (OncePerRequestFilter)
  ├─ no / invalid / expired / forged token → nothing is set (anonymous)
  └─ valid token → SecurityContext(AuthenticatedUser, ROLE_x)  +  TenantContext.set(tenant_id)
  │
  ▼
AuthorizationFilter ── permitAll: /tenant/register, /auth/login, swagger, health
  │                     everything else must be authenticated ──► 401 JSON (RestSecurityErrorHandler)
  ▼
Controller ── @PreAuthorize role checks ──► 403 via GlobalExceptionHandler
  ▼
Service (tenant from TenantContext) ──► tenant-scoped Repository ──► H2
  ▼
finally { TenantContext.clear() }
```

## 5. API request flow

| Endpoint | Role | Behaviour |
|----------|------|-----------|
| `POST /tenant/register` | public | Creates tenant **and** its first ADMIN user (one transaction). |
| `POST /auth/login` | public | Verifies BCrypt password, returns JWT with `tenant_id` + `role`. |
| `POST /users` | ADMIN | Creates a user in the caller's tenant (role in body). |
| `GET /users` | ADMIN | Lists users of the caller's tenant *(extra, for user management)*. |
| `POST /tasks` | MANAGER | Creates a task (`TODO`), tenant from JWT. |
| `PUT /tasks/{id}/assign` | MANAGER | Assigns the task to a user of the same tenant (`{"userId": n}`). |
| `PUT /tasks/{id}/status` | USER | Updates status (`TODO`/`IN_PROGRESS`/`DONE`) of a task **assigned to the caller** only. |
| `GET /tasks` | USER, MANAGER, ADMIN | USER → only assigned tasks; MANAGER/ADMIN → all tasks of the tenant. |

Typical flow: **register tenant → login as admin → create manager & user → login as manager → create task →
assign to user → login as user → update status**.

### Demo with curl

```bash
BASE=http://localhost:8080
J='Content-Type: application/json'

# 1. register tenant + first admin
curl -s -X POST $BASE/tenant/register -H "$J" \
  -d '{"tenantName":"Acme","email":"admin@acme.com","password":"Passw0rd!123"}'

# 2. login as admin
ADMIN=$(curl -s -X POST $BASE/auth/login -H "$J" -d '{"email":"admin@acme.com","password":"Passw0rd!123"}' | jq -r .accessToken)

# 3. admin creates a manager and a user
curl -s -X POST $BASE/users -H "$J" -H "Authorization: Bearer $ADMIN" \
  -d '{"email":"manager@acme.com","password":"Passw0rd!123","role":"MANAGER"}'
curl -s -X POST $BASE/users -H "$J" -H "Authorization: Bearer $ADMIN" \
  -d '{"email":"user@acme.com","password":"Passw0rd!123","role":"USER"}'

# 4. manager creates + assigns a task (user id from step 3)
MGR=$(curl -s -X POST $BASE/auth/login -H "$J" -d '{"email":"manager@acme.com","password":"Passw0rd!123"}' | jq -r .accessToken)
curl -s -X POST $BASE/tasks -H "$J" -H "Authorization: Bearer $MGR" -d '{"title":"Prepare release","description":"v1.0"}'
curl -s -X PUT  $BASE/tasks/1/assign -H "$J" -H "Authorization: Bearer $MGR" -d '{"userId":3}'

# 5. user updates status and lists own tasks
USR=$(curl -s -X POST $BASE/auth/login -H "$J" -d '{"email":"user@acme.com","password":"Passw0rd!123"}' | jq -r .accessToken)
curl -s -X PUT $BASE/tasks/1/status -H "$J" -H "Authorization: Bearer $USR" -d '{"status":"IN_PROGRESS"}'
curl -s $BASE/tasks -H "Authorization: Bearer $USR"

# 6. isolation checks (all rejected)
curl -s "$BASE/tasks?tenant_id=2" -H "Authorization: Bearer $ADMIN"                       # 400
curl -s -X POST $BASE/tasks -H "$J" -H "Authorization: Bearer $MGR" -d '{"title":"x","tenant_id":2}'   # 400
```

### Error format

Every error (validation, security, business, unexpected) has the same JSON shape, produced by
`GlobalExceptionHandler` (controller layer) and `RestSecurityErrorHandler` (filter layer):

```json
{ "timestamp": "2026-10-26T10:15:30Z", "status": 400, "error": "Bad Request",
  "message": "Validation failed", "path": "/tenant/register",
  "fieldErrors": { "password": "password must be 8-72 characters" } }
```

| Status | When |
|--------|------|
| 400 | validation errors, malformed JSON, unknown field (e.g. `tenant_id`), tenant param/header, bad path variable |
| 401 | missing/invalid/expired token, wrong credentials |
| 403 | role not allowed, or USER updating a task not assigned to them |
| 404 | resource missing **or belonging to another tenant** |
| 409 | duplicate tenant name or e-mail |
| 405 / 415 / 500 | wrong method / media type / unexpected error (details hidden, logged server-side) |

## 6. Project structure

```
src/main/java/com/techpulse/taskmanagement
├── config/        SecurityConfig, JwtProperties, OpenApiConfig
├── security/      JwtService, JwtAuthenticationFilter, TenantParameterRejectionFilter,
│                  RestSecurityErrorHandler, AuthenticatedUser, SecurityUtils
├── tenant/        TenantContext (ThreadLocal), TenantOwned, TenantEntityListener
├── domain/        Tenant, User, Task, Role, TaskStatus  (JPA entities / enums)
├── repository/    tenant-scoped Spring Data repositories
├── service/       TenantService, AuthService, UserService, TaskService
├── controller/    TenantController, AuthController, UserController, TaskController
├── dto/           request/response records with Bean Validation
├── exception/     GlobalExceptionHandler, ApiError, custom exceptions
└── util/          EmailNormalizer
src/test/java/...  unit tests (JWT, tenant context/listener, TaskService, SecurityUtils)
                   + MockMvc integration tests (full filter chain, RBAC, cross-tenant attacks)
```

## 7. Assumptions and design decisions

* **E-mail is globally unique** and stored lower-case, because `POST /auth/login` receives only e-mail + password
  (no tenant selector), so the e-mail must identify the tenant.
* `POST /tenant/register` takes `tenantName`, `email`, `password`; the created admin is the first `ADMIN`.
* Roles follow the assignment literally: only **MANAGER** creates/assigns tasks, only **USER** updates task status,
  only **ADMIN** manages users. `GET /tasks`: USER sees assigned tasks, MANAGER and ADMIN see all tasks of the tenant.
* A manager may assign a task to **any user of the same tenant** (any role); re-assignment is allowed.
* Cross-tenant references answer **404** (no existence leak); a same-tenant but unauthorised action answers **403**.
* The `tenants` table's primary key column is named `tenant_id`, so that *every* table has a `tenant_id` column.
* Repositories intentionally do not extend `JpaRepository`, making it impossible to call an unscoped `findAll()`.
* Stateless API (no sessions, CSRF disabled), BCrypt password hashing, no refresh tokens / logout (token expires).
* Hibernate `ddl-auto: update` on an in-memory H2 – data is lost on restart by design.

## 8. Demo-video checklist (≥ 5 min)

1. Requirements recap & architecture (layers, package structure).
2. Walk through JWT claims → `JwtAuthenticationFilter` → `TenantContext` → service → repository.
3. Live demo (Swagger UI or curl): register two tenants, roles, task flow.
4. Attack demo: `tenant_id` in body/query, other tenant's task id, token of tenant A on tenant B data.
5. Global exception handling & validation responses.
6. Tests + JaCoCo report + SonarQube dashboard.
7. Deployment (Render) and README.
