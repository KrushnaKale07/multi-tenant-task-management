package com.techpulse.taskmanagement.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.techpulse.taskmanagement.config.JwtProperties;
import com.techpulse.taskmanagement.entity.Role;
import com.techpulse.taskmanagement.entity.User;
import com.techpulse.taskmanagement.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class TenantIsolationIntegrationTest {

    private static final String PASSWORD = "Passw0rd!123";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JwtProperties jwtProperties;

    private record Tenant(long tenantId, String adminToken, String managerToken, String userToken,
                          long managerId, long userId) {
    }

    // ------------------------------------------------------------------ helpers

    private JsonNode call(HttpMethod method, String url, String token, Object body, int expectedStatus)
            throws Exception {
        MockHttpServletRequestBuilder builder = request(method, url).contentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        if (body != null) {
            builder.content(objectMapper.writeValueAsString(body));
        }
        MvcResult result = mockMvc.perform(builder).andExpect(status().is(expectedStatus)).andReturn();
        String content = result.getResponse().getContentAsString();
        return content.isBlank() ? objectMapper.nullNode() : objectMapper.readTree(content);
    }

    private String login(String email) throws Exception {
        return call(HttpMethod.POST, "/auth/login", null, Map.of("email", email, "password", PASSWORD), 200)
                .get("accessToken").asText();
    }

    private Tenant createTenant(String label) throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String domain = label + suffix + ".com";
        String adminEmail = "admin@" + domain;

        JsonNode registration = call(HttpMethod.POST, "/tenant/register", null,
                Map.of("tenantName", label + "-" + suffix, "email", adminEmail, "password", PASSWORD), 201);
        String adminToken = login(adminEmail);

        JsonNode manager = call(HttpMethod.POST, "/users", adminToken,
                Map.of("email", "manager@" + domain, "password", PASSWORD, "role", "MANAGER"), 201);
        JsonNode user = call(HttpMethod.POST, "/users", adminToken,
                Map.of("email", "user@" + domain, "password", PASSWORD, "role", "USER"), 201);

        return new Tenant(registration.get("tenantId").asLong(), adminToken,
                login("manager@" + domain), login("user@" + domain),
                manager.get("id").asLong(), user.get("id").asLong());
    }

    private long createTask(Tenant tenant, String title) throws Exception {
        return call(HttpMethod.POST, "/tasks", tenant.managerToken(),
                Map.of("title", title, "description", "details"), 201).get("id").asLong();
    }

    private JsonNode claimsOf(String token) throws Exception {
        String payload = token.split("\\.")[1];
        return objectMapper.readTree(new String(Base64.getUrlDecoder().decode(payload), StandardCharsets.UTF_8));
    }

    // ------------------------------------------------------------------ registration & login

    @Test
    void registerCreatesTenantWithAdminAndRejectsDuplicates() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Map<String, String> body = Map.of("tenantName", "Acme " + suffix,
                "email", "Boss@acme" + suffix + ".com", "password", PASSWORD);

        JsonNode created = call(HttpMethod.POST, "/tenant/register", null, body, 201);
        assertThat(created.get("tenantId").asLong()).isPositive();
        assertThat(created.get("adminEmail").asText()).isEqualTo("boss@acme" + suffix + ".com");

        // same tenant name and same e-mail -> 409
        call(HttpMethod.POST, "/tenant/register", null, body, 409);
        call(HttpMethod.POST, "/tenant/register", null,
                Map.of("tenantName", "Other " + suffix, "email", "boss@acme" + suffix + ".com",
                        "password", PASSWORD), 409);
    }

    @Test
    void registerValidatesPayload() throws Exception {
        JsonNode error = call(HttpMethod.POST, "/tenant/register", null,
                Map.of("tenantName", "", "email", "not-an-email", "password", "short"), 400);

        assertThat(error.get("message").asText()).isEqualTo("Validation failed");
        assertThat(error.get("fieldErrors").has("tenantName")).isTrue();
        assertThat(error.get("fieldErrors").has("email")).isTrue();
        assertThat(error.get("fieldErrors").has("password")).isTrue();
    }

    @Test
    void malformedJsonIsRejected() throws Exception {
        mockMvc.perform(request(HttpMethod.POST, "/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void loginReturnsJwtWithTenantIdAndRole() throws Exception {
        Tenant tenant = createTenant("claims");

        JsonNode response = call(HttpMethod.POST, "/auth/login", null,
                Map.of("email", claimsOf(tenant.managerToken()).get("sub").asText(), "password", PASSWORD), 200);
        JsonNode claims = claimsOf(response.get("accessToken").asText());

        assertThat(response.get("tokenType").asText()).isEqualTo("Bearer");
        assertThat(claims.get("tenant_id").asLong()).isEqualTo(tenant.tenantId());
        assertThat(claims.get("role").asText()).isEqualTo("MANAGER");
        assertThat(claims.get("user_id").asLong()).isEqualTo(tenant.managerId());
        assertThat(claims.has("exp")).isTrue();
    }

    @Test
    void loginFailsForWrongPasswordUnknownUserAndInvalidPayload() throws Exception {
        Tenant tenant = createTenant("badlogin");
        String email = claimsOf(tenant.adminToken()).get("sub").asText();

        call(HttpMethod.POST, "/auth/login", null, Map.of("email", email, "password", "wrong-password"), 401);
        call(HttpMethod.POST, "/auth/login", null,
                Map.of("email", "nobody@nowhere.com", "password", PASSWORD), 401);
        call(HttpMethod.POST, "/auth/login", null, Map.of("email", "bad", "password", ""), 400);
    }

    // ------------------------------------------------------------------ authentication

    @Test
    void securedEndpointsRequireValidJwt() throws Exception {
        call(HttpMethod.GET, "/tasks", null, null, 401);
        call(HttpMethod.GET, "/tasks", "garbage.token.value", null, 401);

        Tenant tenant = createTenant("expired");
        // token signed with the right key but already expired
        JwtService expiring = new JwtService(new JwtProperties(jwtProperties.secret(), -5));
        String expired = expiring.generateToken(User.builder()
                .id(tenant.userId()).email("x@y.com").role(Role.USER).tenantId(tenant.tenantId()).build());
        call(HttpMethod.GET, "/tasks", expired, null, 401);

        // token signed with a foreign key
        JwtService forger = new JwtService(new JwtProperties("forged-secret-forged-secret-forged-secret-123456", 60));
        String forged = forger.generateToken(User.builder()
                .id(tenant.userId()).email("x@y.com").role(Role.ADMIN).tenantId(tenant.tenantId()).build());
        call(HttpMethod.GET, "/tasks", forged, null, 401);
    }

    @Test
    void publicEndpointsStayReachable() throws Exception {
        call(HttpMethod.GET, "/actuator/health", null, null, 200);
    }

    // ------------------------------------------------------------------ tenant must never come from the client

    @Test
    void tenantIdInBodyQueryOrHeaderIsRejected() throws Exception {
        Tenant tenant = createTenant("clientTenant");

        JsonNode bodyError = call(HttpMethod.POST, "/tasks", tenant.managerToken(),
                Map.of("title", "sneaky", "tenant_id", 999), 400);
        assertThat(bodyError.get("message").asText()).contains("tenant");

        call(HttpMethod.POST, "/users", tenant.adminToken(),
                Map.of("email", "x@x.com", "password", PASSWORD, "role", "USER", "tenantId", 999), 400);

        JsonNode queryError = call(HttpMethod.GET, "/tasks?tenant_id=999", tenant.adminToken(), null, 400);
        assertThat(queryError.get("message").asText()).contains("JWT");
        call(HttpMethod.GET, "/tasks?foo=1&tenantId=999", tenant.adminToken(), null, 400);

        mockMvc.perform(request(HttpMethod.GET, "/tasks")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenant.adminToken())
                        .header("X-Tenant-Id", "999"))
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------ role based access

    @Test
    void rolesAreEnforced() throws Exception {
        Tenant tenant = createTenant("rbac");
        Map<String, Object> newUser = Map.of("email", "z@z" + UUID.randomUUID() + ".com",
                "password", PASSWORD, "role", "USER");
        Map<String, String> newTask = Map.of("title", "t");

        call(HttpMethod.POST, "/users", tenant.managerToken(), newUser, 403);
        call(HttpMethod.POST, "/users", tenant.userToken(), newUser, 403);
        call(HttpMethod.GET, "/users", tenant.managerToken(), null, 403);

        call(HttpMethod.POST, "/tasks", tenant.adminToken(), newTask, 403);
        call(HttpMethod.POST, "/tasks", tenant.userToken(), newTask, 403);

        long taskId = createTask(tenant, "rbac task");
        call(HttpMethod.PUT, "/tasks/" + taskId + "/assign", tenant.userToken(),
                Map.of("userId", tenant.userId()), 403);
        call(HttpMethod.PUT, "/tasks/" + taskId + "/assign", tenant.adminToken(),
                Map.of("userId", tenant.userId()), 403);
        call(HttpMethod.PUT, "/tasks/" + taskId + "/status", tenant.managerToken(),
                Map.of("status", "DONE"), 403);
    }

    // ------------------------------------------------------------------ task lifecycle

    @Test
    void taskLifecycleWithinOneTenant() throws Exception {
        Tenant tenant = createTenant("flow");

        JsonNode created = call(HttpMethod.POST, "/tasks", tenant.managerToken(),
                Map.of("title", "  Prepare release ", "description", "v1"), 201);
        long taskId = created.get("id").asLong();
        assertThat(created.get("tenantId").asLong()).isEqualTo(tenant.tenantId()); // taken from JWT
        assertThat(created.get("status").asText()).isEqualTo("TODO");
        assertThat(created.get("title").asText()).isEqualTo("Prepare release");

        // unassigned task is invisible to the USER and cannot be updated by them
        call(HttpMethod.PUT, "/tasks/" + taskId + "/status", tenant.userToken(), Map.of("status", "DONE"), 403);
        assertThat(call(HttpMethod.GET, "/tasks", tenant.userToken(), null, 200)).isEmpty();

        JsonNode assigned = call(HttpMethod.PUT, "/tasks/" + taskId + "/assign", tenant.managerToken(),
                Map.of("userId", tenant.userId()), 200);
        assertThat(assigned.get("assignedTo").asLong()).isEqualTo(tenant.userId());

        JsonNode updated = call(HttpMethod.PUT, "/tasks/" + taskId + "/status", tenant.userToken(),
                Map.of("status", "IN_PROGRESS"), 200);
        assertThat(updated.get("status").asText()).isEqualTo("IN_PROGRESS");

        createTask(tenant, "second task, not assigned");
        assertThat(call(HttpMethod.GET, "/tasks", tenant.userToken(), null, 200)).hasSize(1);
        assertThat(call(HttpMethod.GET, "/tasks", tenant.managerToken(), null, 200)).hasSize(2);
        assertThat(call(HttpMethod.GET, "/tasks", tenant.adminToken(), null, 200)).hasSize(2);
        assertThat(call(HttpMethod.GET, "/users", tenant.adminToken(), null, 200)).hasSize(3);
    }

    @Test
    void taskRequestsAreValidated() throws Exception {
        Tenant tenant = createTenant("validate");
        long taskId = createTask(tenant, "to validate");

        JsonNode blankTitle = call(HttpMethod.POST, "/tasks", tenant.managerToken(), Map.of("title", " "), 400);
        assertThat(blankTitle.get("fieldErrors").has("title")).isTrue();

        call(HttpMethod.PUT, "/tasks/" + taskId + "/assign", tenant.managerToken(), Map.of(), 400);
        call(HttpMethod.PUT, "/tasks/" + taskId + "/assign", tenant.managerToken(), Map.of("userId", -1), 400);
        call(HttpMethod.PUT, "/tasks/" + taskId + "/status", tenant.userToken(), Map.of("status", "WHATEVER"), 400);
        call(HttpMethod.PUT, "/tasks/abc/assign", tenant.managerToken(), Map.of("userId", 1), 400);
        call(HttpMethod.POST, "/users", tenant.adminToken(),
                Map.of("email", "bad", "password", "x"), 400);
    }

    @Test
    void duplicateUserEmailIsRejected() throws Exception {
        Tenant tenant = createTenant("dup");
        String email = claimsOf(tenant.userToken()).get("sub").asText();

        call(HttpMethod.POST, "/users", tenant.adminToken(),
                Map.of("email", email, "password", PASSWORD, "role", "USER"), 409);
    }

    // ------------------------------------------------------------------ CROSS-TENANT ISOLATION

    @Test
    void tenantsCanNeverSeeOrModifyEachOthersData() throws Exception {
        Tenant a = createTenant("tenantA");
        Tenant b = createTenant("tenantB");

        long taskOfA = createTask(a, "secret of A");
        call(HttpMethod.PUT, "/tasks/" + taskOfA + "/assign", a.managerToken(), Map.of("userId", a.userId()), 200);
        createTask(b, "task of B");

        // listing: each tenant only sees its own data
        JsonNode tasksOfB = call(HttpMethod.GET, "/tasks", b.adminToken(), null, 200);
        assertThat(tasksOfB).hasSize(1);
        assertThat(tasksOfB.get(0).get("title").asText()).isEqualTo("task of B");
        assertThat(tasksOfB.get(0).get("tenantId").asLong()).isEqualTo(b.tenantId());
        assertThat(call(HttpMethod.GET, "/tasks", a.adminToken(), null, 200)).hasSize(1);
        assertThat(call(HttpMethod.GET, "/users", b.adminToken(), null, 200)).hasSize(3);

        // B's manager cannot assign A's task (even to B's own user) -> 404, existence is not leaked
        call(HttpMethod.PUT, "/tasks/" + taskOfA + "/assign", b.managerToken(), Map.of("userId", b.userId()), 404);

        // A's manager cannot assign A's task to a user of tenant B -> 404
        call(HttpMethod.PUT, "/tasks/" + taskOfA + "/assign", a.managerToken(), Map.of("userId", b.userId()), 404);

        // B's user cannot touch A's task -> 404
        call(HttpMethod.PUT, "/tasks/" + taskOfA + "/status", b.userToken(), Map.of("status", "DONE"), 404);

        // A's task is untouched
        JsonNode tasksOfA = call(HttpMethod.GET, "/tasks", a.userToken(), null, 200);
        assertThat(tasksOfA).hasSize(1);
        assertThat(tasksOfA.get(0).get("status").asText()).isEqualTo("TODO");
        assertThat(tasksOfA.get(0).get("assignedTo").asLong()).isEqualTo(a.userId());
    }

    // ------------------------------------------------------------------ generic error handling

    @Test
    void unknownRouteAndWrongMethodUseJsonErrors() throws Exception {
        Tenant tenant = createTenant("errors");

        JsonNode notFound = call(HttpMethod.GET, "/does-not-exist", tenant.adminToken(), null, 404);
        assertThat(notFound.get("status").asInt()).isEqualTo(404);

        call(HttpMethod.GET, "/auth/login", null, null, 405);
        call(HttpMethod.DELETE, "/tasks", tenant.adminToken(), null, 405);
        call(HttpMethod.PUT, "/tasks/1/assign", tenant.managerToken(), null, 400);

        mockMvc.perform(request(HttpMethod.POST, "/auth/login")
                        .contentType(MediaType.TEXT_PLAIN).content("x"))
                .andExpect(status().isUnsupportedMediaType());
    }
}
