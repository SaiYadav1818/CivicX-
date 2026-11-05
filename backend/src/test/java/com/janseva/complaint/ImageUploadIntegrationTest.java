package com.janseva.complaint;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class ImageUploadIntegrationTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("janseva_test")
            .withUsername("janseva")
            .withPassword("janseva");

    @DynamicPropertySource
    static void storageProperties(DynamicPropertyRegistry registry) {
        registry.add("app.storage.local-dir", () -> "target/test-storage-images");
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

    private long createComplaint(String token) throws Exception {
        String body = """
                {
                  "categoryId": 1,
                  "title": "Garbage dump",
                  "description": "Garbage near the market road.",
                  "latitude": 17.4840,
                  "longitude": 78.3910,
                  "locationSource": "DEVICE_GPS"
                }
                """;
        String response = mockMvc.perform(post("/api/v1/complaints")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    private byte[] jpegBytes() throws Exception {
        BufferedImage image = new BufferedImage(64, 48, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.ORANGE);
        g.fillRect(0, 0, 64, 48);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", out);
        return out.toByteArray();
    }

    private byte[] pngBytes() throws Exception {
        BufferedImage image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private byte[] webpBytes() {
        byte[] header = new byte[]{'R', 'I', 'F', 'F', 0x00, 0x00, 0x00, 0x00, 'W', 'E', 'B', 'P'};
        byte[] payload = "placeholder-webp-payload".getBytes(StandardCharsets.UTF_8);
        byte[] data = new byte[header.length + payload.length];
        System.arraycopy(header, 0, data, 0, header.length);
        System.arraycopy(payload, 0, data, header.length, payload.length);
        return data;
    }

    @Test
    void acceptsJpegPngAndWebpByContent() throws Exception {
        String token = registerCitizen("img.valid@test.local");
        long complaintId = createComplaint(token);

        MockMultipartFile jpeg = new MockMultipartFile("file", "photo.jpg", "image/jpeg", jpegBytes());
        String jpegResponse = mockMvc.perform(multipart("/api/v1/complaints/{id}/images", complaintId).file(jpeg)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.contentType").value("image/jpeg"))
                .andExpect(jsonPath("$.width").value(64))
                .andExpect(jsonPath("$.height").value(48))
                .andReturn().getResponse().getContentAsString();
        long jpegMediaId = objectMapper.readTree(jpegResponse).get("id").asLong();

        MockMultipartFile png = new MockMultipartFile("file", "map.png", "image/png", pngBytes());
        mockMvc.perform(multipart("/api/v1/complaints/{id}/images", complaintId).file(png)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.contentType").value("image/png"));

        MockMultipartFile webp = new MockMultipartFile("file", "scene.webp", "image/webp", webpBytes());
        mockMvc.perform(multipart("/api/v1/complaints/{id}/images", complaintId).file(webp)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.contentType").value("image/webp"));

        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/complaints/{id}/images/{mediaId}", complaintId, jpegMediaId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG));
    }

    @Test
    void rejectsUnsupportedFakeAndOversizedFiles() throws Exception {
        String token = registerCitizen("img.invalid@test.local");
        long complaintId = createComplaint(token);

        MockMultipartFile gif = new MockMultipartFile("file", "anim.gif", "image/gif",
                "GIF89a-not-a-real-gif".getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(multipart("/api/v1/complaints/{id}/images", complaintId).file(gif)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnsupportedMediaType());

        MockMultipartFile fakeJpeg = new MockMultipartFile("file", "fake.jpg", "image/jpeg",
                "this is just text".getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(multipart("/api/v1/complaints/{id}/images", complaintId).file(fakeJpeg)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnsupportedMediaType());

        byte[] huge = new byte[6 * 1024 * 1024];
        huge[0] = (byte) 0xFF;
        huge[1] = (byte) 0xD8;
        huge[2] = (byte) 0xFF;
        MockMultipartFile oversized = new MockMultipartFile("file", "huge.jpg", "image/jpeg", huge);
        mockMvc.perform(multipart("/api/v1/complaints/{id}/images", complaintId).file(oversized)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isPayloadTooLarge());
    }

    @Test
    void sanitizesPathTraversalFilename() throws Exception {
        String token = registerCitizen("img.traversal@test.local");
        long complaintId = createComplaint(token);

        MockMultipartFile evil = new MockMultipartFile("file", "../../evil.jpg", "image/jpeg", jpegBytes());
        String response = mockMvc.perform(multipart("/api/v1/complaints/{id}/images", complaintId).file(evil)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        JsonNode json = objectMapper.readTree(response);
        assertThat(json.get("originalFilename").asText()).isEqualTo("evil.jpg");
        assertThat(json.has("objectKey")).isFalse();
        assertThat(json.toString()).doesNotContain("object-storage");
    }

    @Test
    void onlyOwnerCanUploadAndDownload() throws Exception {
        String tokenA = registerCitizen("img.owner.a@test.local");
        String tokenB = registerCitizen("img.owner.b@test.local");
        long complaintId = createComplaint(tokenA);

        MockMultipartFile jpeg = new MockMultipartFile("file", "photo.jpg", "image/jpeg", jpegBytes());
        mockMvc.perform(multipart("/api/v1/complaints/{id}/images", complaintId).file(jpeg)
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isForbidden());

        String uploaded = mockMvc.perform(multipart("/api/v1/complaints/{id}/images", complaintId).file(jpeg)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long mediaId = objectMapper.readTree(uploaded).get("id").asLong();

        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/complaints/{id}/images/{mediaId}", complaintId, mediaId)
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isForbidden());
    }

    @Test
    void enforcesMaximumImagesPerComplaint() throws Exception {
        String token = registerCitizen("img.limit@test.local");
        long complaintId = createComplaint(token);
        MockMultipartFile jpeg = new MockMultipartFile("file", "photo.jpg", "image/jpeg", jpegBytes());

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(multipart("/api/v1/complaints/{id}/images", complaintId).file(jpeg)
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isCreated());
        }
        mockMvc.perform(multipart("/api/v1/complaints/{id}/images", complaintId).file(jpeg)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict());
    }

    @Test
    void imageUploadRequiresAuthentication() throws Exception {
        MockMultipartFile jpeg = new MockMultipartFile("file", "photo.jpg", "image/jpeg", jpegBytes());
        mockMvc.perform(multipart("/api/v1/complaints/1/images").file(jpeg))
                .andExpect(status().isUnauthorized());
    }
}
