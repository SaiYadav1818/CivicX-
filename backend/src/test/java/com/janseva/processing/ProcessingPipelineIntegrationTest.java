package com.janseva.processing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.janseva.ai.AiAnalysisResultRepository;
import com.janseva.duplicate.DuplicateComplaintLinkRepository;
import com.janseva.duplicate.DuplicateDetectionResultRepository;
import com.janseva.embedding.ImageEmbeddingRepository;
import com.janseva.hashing.ImageHashRepository;
import com.janseva.outbox.ProcessingOutbox;
import com.janseva.outbox.ProcessingOutboxRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class ProcessingPipelineIntegrationTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("janseva_test")
            .withUsername("janseva")
            .withPassword("janseva");

    @DynamicPropertySource
    static void testProperties(DynamicPropertyRegistry registry) {
        registry.add("app.storage.local-dir", () -> "target/test-storage-processing");
        registry.add("spring.rabbitmq.listener.simple.auto-startup", () -> false);
        registry.add("civicx.outbox.poll-ms", () -> 3600000);
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private ComplaintProcessingService processingService;
    @Autowired
    private AiAnalysisResultRepository aiAnalysisResultRepository;
    @Autowired
    private ImageHashRepository imageHashRepository;
    @Autowired
    private ImageEmbeddingRepository imageEmbeddingRepository;
    @Autowired
    private DuplicateDetectionResultRepository duplicateResultRepository;
    @Autowired
    private DuplicateComplaintLinkRepository duplicateLinkRepository;
    @Autowired
    private ProcessingOutboxRepository outboxRepository;

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

    private long createComplaint(String token, String title, String description, double lat, double lng)
            throws Exception {
        String body = """
                {
                  "categoryId": 1,
                  "title": "%s",
                  "description": "%s",
                  "latitude": %f,
                  "longitude": %f,
                  "locationSource": "DEVICE_GPS"
                }
                """.formatted(title, description, lat, lng);
        String response = mockMvc.perform(post("/api/v1/complaints")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    private void uploadImage(String token, long complaintId) throws Exception {
        BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(200, 90, 40));
        g.fillRect(0, 0, 64, 64);
        g.setColor(Color.DARK_GRAY);
        g.fillRect(10, 10, 20, 20);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", out);
        MockMultipartFile file = new MockMultipartFile("file", "evidence.jpg", "image/jpeg", out.toByteArray());
        mockMvc.perform(multipart("/api/v1/complaints/{id}/images", complaintId).file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated());
    }

    @Test
    void complaintCreationPersistsOutboxEventEvenWithoutBroker() throws Exception {
        String token = registerCitizen("pipeline.outbox@test.local");
        long complaintId = createComplaint(token, "Garbage", "Garbage near the temple", 17.4840, 78.3910);

        ProcessingOutbox outbox = outboxRepository.findByAggregateTypeAndAggregateIdAndEventType(
                ProcessingOutbox.AGGREGATE_COMPLAINT, complaintId,
                ProcessingOutbox.EVENT_COMPLAINT_CREATED).orElseThrow();

        assertThat(outbox.getPayload()).contains(String.valueOf(complaintId));
        assertThat(outbox.getStatus()).isIn(com.janseva.outbox.OutboxStatus.PENDING, com.janseva.outbox.OutboxStatus.SENT);
    }

    @Test
    void pipelineClassifiesAndHashesImages() throws Exception {
        String token = registerCitizen("pipeline.classify@test.local");
        long complaintId = createComplaint(token, "Garbage problem",
                "Garbage has accumulated near the market and there is a lot of plastic waste.", 17.4840, 78.3910);
        uploadImage(token, complaintId);

        processingService.process(complaintId);
        processingService.process(complaintId);

        assertThat(aiAnalysisResultRepository.findByComplaintIdOrderByCreatedAtAsc(complaintId)).hasSize(1);
        var result = aiAnalysisResultRepository.findByComplaintIdOrderByCreatedAtAsc(complaintId).get(0);
        assertThat(result.getModelName()).contains("rule-based");
        assertThat(result.getCategoryCode()).isEqualTo("GARBAGE");

        assertThat(imageHashRepository.findAll()).isNotEmpty();
        var hash = imageHashRepository.findAll().get(0);
        assertThat(hash.getSha256()).hasSize(64);
        assertThat(hash.getPhash()).hasSize(16);

        assertThat(imageEmbeddingRepository.findAll()).isNotEmpty();
        var embedding = imageEmbeddingRepository.findAll().get(0);
        assertThat(embedding.getModelName()).isEqualTo("civicx-local-embedding");
        double[] vector = objectMapper.readValue(embedding.getVector(), double[].class);
        assertThat(vector).hasSize(128);

        mockMvc.perform(get("/api/v1/complaints/{id}/ai-summary", complaintId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categorySuggestion.code").value("GARBAGE"))
                .andExpect(jsonPath("$.categorySuggestion.method").value("RULE_BASED"));

        String admin = adminToken();
        mockMvc.perform(get("/api/v1/admin/complaints/{id}/ai-results", complaintId)
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].modelName").value("civicx-rule-based-classifier"));
    }

    @Test
    void nearbySimilarComplaintsProduceReviewableCandidates() throws Exception {
        String token = registerCitizen("pipeline.dup@test.local");
        long first = createComplaint(token, "Garbage pile", "Huge garbage pile near the school entrance.", 17.4840, 78.3910);
        uploadImage(token, first);
        processingService.process(first);

        long second = createComplaint(token, "Garbage dump", "Large garbage dump near the school main gate.", 17.4842, 78.3912);
        uploadImage(token, second);
        processingService.process(second);

        String admin = adminToken();
        String list = mockMvc.perform(get("/api/v1/admin/complaints/{id}/duplicates", second)
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode candidates = objectMapper.readTree(list);
        assertThat(candidates).isNotEmpty();
        JsonNode candidate = null;
        for (JsonNode c : candidates) {
            if (c.get("matchedComplaintId").asLong() == first) {
                candidate = c;
                break;
            }
        }
        assertThat(candidate).as("candidate matching the first complaint").isNotNull();
        long matchedId = candidate.get("matchedComplaintId").asLong();
        assertThat(candidate.get("distanceMeters").asDouble()).isGreaterThan(0).isLessThan(100);
        assertThat(candidate.get("textSimilarity").asDouble()).isGreaterThan(0.2);

        mockMvc.perform(post("/api/v1/admin/complaints/{id}/duplicates/{mid}/confirm", second, matchedId)
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\": \"Same location and photo\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewStatus").value("CONFIRMED"));

        assertThat(duplicateLinkRepository.findByPrimaryComplaintId(matchedId)).isNotEmpty();

        mockMvc.perform(post("/api/v1/admin/complaints/{id}/duplicates/{mid}/confirm", second, matchedId)
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/api/v1/complaints/{id}/ai-summary", second)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.possibleDuplicate").value(true))
                .andExpect(jsonPath("$.topMatches[0].complaintNumber").isNotEmpty())
                .andExpect(jsonPath("$.topMatches[0].distanceMeters").isNumber())
                .andExpect(jsonPath("$.topMatches[0].latitude").doesNotExist())
                .andExpect(jsonPath("$.topMatches[0].email").doesNotExist());
    }

    @Test
    void adminCanDismissCandidateAndRetryFailsWhenProcessed() throws Exception {
        String token = registerCitizen("pipeline.dismiss@test.local");
        long first = createComplaint(token, "Garbage", "Garbage near the bus stand", 17.5000, 78.4000);
        uploadImage(token, first);
        processingService.process(first);

        long second = createComplaint(token, "Garbage again", "Garbage dumped near the bus stand", 17.5001, 78.4001);
        uploadImage(token, second);
        processingService.process(second);

        String admin = adminToken();
        String list = mockMvc.perform(get("/api/v1/admin/complaints/{id}/duplicates", second)
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long matchedId = objectMapper.readTree(list).get(0).get("matchedComplaintId").asLong();

        mockMvc.perform(post("/api/v1/admin/complaints/{id}/duplicates/{mid}/dismiss", second, matchedId)
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\": \"Different physical issue\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewStatus").value("DISMISSED"));

        assertThat(duplicateLinkRepository.findByPrimaryComplaintId(matchedId)).isEmpty();
    }

    @Test
    void citizensCannotAccessDuplicateReviewOrOtherCitizensAiSummary() throws Exception {
        String citizenA = registerCitizen("pipeline.priv.a@test.local");
        String citizenB = registerCitizen("pipeline.priv.b@test.local");
        long complaintId = createComplaint(citizenA, "Garbage", "Garbage near the well", 17.4840, 78.3910);

        mockMvc.perform(get("/api/v1/complaints/{id}/ai-summary", complaintId)
                        .header("Authorization", "Bearer " + citizenB))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/admin/complaints/{id}/duplicates/999/confirm", complaintId)
                        .header("Authorization", "Bearer " + citizenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());

        String admin = adminToken();
        processingService.process(complaintId);
        mockMvc.perform(post("/api/v1/admin/complaints/{id}/ai-processing/retry", complaintId)
                        .header("Authorization", "Bearer " + citizenA))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/admin/complaints/{id}/ai-processing/retry", complaintId)
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isConflict());
    }
}
