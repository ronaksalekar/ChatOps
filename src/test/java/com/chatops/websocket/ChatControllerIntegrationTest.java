package com.chatops.websocket;

import com.chatops.AbstractIntegrationTest;
import com.chatops.auth.dto.AuthResponse;
import com.chatops.auth.dto.LoginRequest;
import com.chatops.auth.dto.RegisterRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for the WebSocket chat controller logic.
 *
 * <p>These tests exercise the HTTP layer (auth + room creation) that underpins
 * the WebSocket flows. Full STOMP WebSocket integration requires a running
 * server; for in-process STOMP testing see {@code StompChannelInterceptorTest}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ChatControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    private String accessToken;

    @BeforeEach
    void registerAndLogin() throws Exception {
        // Register
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegisterRequest("wschatuser", "ws@chatops.test", "Password1!"))))
                .andReturn(); // Ignore if already registered

        // Login
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest("wschatuser", "Password1!"))))
                .andExpect(status().isOk())
                .andReturn();

        AuthResponse auth = objectMapper.readValue(
                result.getResponse().getContentAsString(), AuthResponse.class);
        accessToken = auth.accessToken();
    }

    @Test
    void createRoomEndpoint_returnsRoomDetails() throws Exception {
        mockMvc.perform(post("/api/rooms")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"ws-test-room\",\"description\":\"WebSocket test\",\"isPrivate\":false}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("ws-test-room"))
                .andExpect(jsonPath("$.memberCount").value(1));
    }
}
