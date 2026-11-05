package com.janseva.complaint;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class ComplaintFlowIntegrationTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("janseva_test")
            .withUsername("janseva")
            .withPassword("janseva");

    @DynamicPropertySource
    static void storageProperties(DynamicPropertyRegistry registry) {
        registry.add("app.storage.local-dir", () -> "target/test-storage-flow");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String registerCitizen(String email) throws Exception {
        String body = """
                {"fullName": "Citizen", "email": "%s", "password": "Password1"}
                """.formatted(email);
        String response = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("accessToken").asText();
    }

    private String adminToken() throws Exception {
        String body = """
                {"email": "admin@janseva.local", "password": "Admin@123"}
                """;
        String response = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("accessToken").asText();
    }

    private String submitComplaint(String token, String body) throws Exception {
        return mockMvc.perform(post("/api/v1/complaints")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn().getResponse().getContentAsString();
    }

    private static final String VALID_COMPLAINT = """
            {
              "categoryId": 1,
              "title": "Garbage dumped beside main road",
              "description": "Garbage has been accumulating near the bus stop for a week.",
              "latitude": 17.4840,
              "longitude": 78.3910,
              "accuracyMeters": 12.5,
              "address": "Kukatpally, Hyderabad",
              "landmark": "Near bus stop",
              "city": "Hyderabad",
              "state": "Telangana",
              "postalCode": "500072",
              "locationSource": "DEVICE_GPS"
            }
            """;

    @Test
    void citizenSubmitsComplaintAndGetsUniqueReference() throws Exception {
        String token = registerCitizen("flow.citizen@test.local");

        String response = mockMvc.perform(post("/api/v1/complaints")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_COMPLAINT))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andReturn().getResponse().getContentAsString();

        JsonNode json = objectMapper.readTree(response);
        assertThat(json.get("complaintNumber").asText()).matches("CX-\\d{6}");
        assertThat(json.get("location").get("latitude").asDouble()).isEqualTo(17.4840);
        assertThat(json.get("location").get("landmark").asText()).isEqualTo("Near bus stop");

        long id = json.get("id").asLong();
        mockMvc.perform(get("/api/v1/complaints/{id}", id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.complaintNumber").value(json.get("complaintNumber").asText()));

        mockMvc.perform(get("/api/v1/complaints/my")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(id));
    }

    @Test
    void rejectsInvalidCategoryMissingDescriptionAndBadGps() throws Exception {
        String token = registerCitizen("flow.invalid@test.local");

        String invalidCategory = VALID_COMPLAINT.replace("\"categoryId\": 1", "\"categoryId\": 9999");
        mockMvc.perform(post("/api/v1/complaints")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(invalidCategory))
                .andExpect(status().isBadRequest());

        String noDescription = VALID_COMPLAINT.replace(
                "\"description\": \"Garbage has been accumulating near the bus stop for a week.\"",
                "\"description\": \"\"");
        mockMvc.perform(post("/api/v1/complaints")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(noDescription))
                .andExpect(status().isBadRequest());

        String badLat = VALID_COMPLAINT.replace("17.4840", "91.5");
        mockMvc.perform(post("/api/v1/complaints")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(badLat))
                .andExpect(status().isBadRequest());

        String badLng = VALID_COMPLAINT.replace("78.3910", "-181.0");
        mockMvc.perform(post("/api/v1/complaints")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(badLng))
                .andExpect(status().isBadRequest());
    }

    @Test
    void citizenCannotSeeAnotherCitizensComplaint() throws Exception {
        String tokenA = registerCitizen("flow.idor.a@test.local");
        String tokenB = registerCitizen("flow.idor.b@test.local");

        JsonNode complaint = objectMapper.readTree(submitComplaint(tokenA, VALID_COMPLAINT));
        long id = complaint.get("id").asLong();

        mockMvc.perform(get("/api/v1/complaints/{id}", id)
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/complaints/{id}/history", id)
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedAndCitizenRequestsToAdminApiAreRejected() throws Exception {
        mockMvc.perform(get("/api/v1/admin/complaints"))
                .andExpect(status().isUnauthorized());

        String citizenToken = registerCitizen("flow.noadmin@test.local");
        mockMvc.perform(get("/api/v1/admin/complaints")
                        .header("Authorization", "Bearer " + citizenToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminVerifiesRejectsAndWorkflowIsEnforced() throws Exception {
        String citizenToken = registerCitizen("flow.workflow@test.local");
        String adminToken = adminToken();

        JsonNode complaint = objectMapper.readTree(submitComplaint(citizenToken, VALID_COMPLAINT));
        long id = complaint.get("id").asLong();

        mockMvc.perform(get("/api/v1/admin/complaints")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("status", "SUBMITTED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));

        mockMvc.perform(post("/api/v1/admin/complaints/{id}/verify", id)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\": \"Location confirmed\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VERIFIED"))
                .andExpect(jsonPath("$.verifiedAt").isNotEmpty());

        mockMvc.perform(patch("/api/v1/admin/complaints/{id}/status", id)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"CLOSED\"}"))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/v1/admin/complaints/{id}/reject", id)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\": \"Not a civic issue\"}"))
                .andExpect(status().isConflict());

        JsonNode rejected = objectMapper.readTree(submitComplaint(citizenToken, VALID_COMPLAINT));
        long rejectId = rejected.get("id").asLong();

        mockMvc.perform(post("/api/v1/admin/complaints/{id}/reject", rejectId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\": \"   \"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/admin/complaints/{id}/reject", rejectId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\": \"Photo does not show any civic issue\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));

        mockMvc.perform(get("/api/v1/admin/complaints/{id}/history", rejectId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(2)));

        mockMvc.perform(get("/api/v1/complaints/{id}/history", rejectId)
                        .header("Authorization", "Bearer " + citizenToken))
                .andExpect(status().isOk());
    }

    @Test
    void adminFiltersByCategoryAndDateRange() throws Exception {
        String citizenToken = registerCitizen("flow.filter@test.local");
        String adminToken = adminToken();
        submitComplaint(citizenToken, VALID_COMPLAINT);

        mockMvc.perform(get("/api/v1/admin/complaints")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("categoryId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));

        mockMvc.perform(get("/api/v1/admin/complaints")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("categoryId", "13")
                        .param("status", "SUBMITTED"))
                .andExpect(status().isOk());

        MvcResult result = mockMvc.perform(get("/api/v1/admin/complaints")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("from", "2030-01-01T00:00:00Z"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode page = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(page.get("totalElements").asLong()).isZero();
    }
}
