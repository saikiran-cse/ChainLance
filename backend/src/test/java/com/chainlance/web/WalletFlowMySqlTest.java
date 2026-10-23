package com.chainlance.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chainlance.wallet.WalletTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.Keys;

/**
 * Opt-in end-to-end wallet linking against a real MySQL TEST database, with real JWTs and real signatures
 * made by throwaway keys. Skipped unless TEST_DB_URL is set (it must contain "_test"). Each test rolls back.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "chainlance.jwt.secret=test-only-secret-test-only-secret-0123456789",
        "chainlance.admin-bootstrap.email=",
        "chainlance.admin-bootstrap.password="
})
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = ".+")
class WalletFlowMySqlTest {

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        String url = System.getenv("TEST_DB_URL");
        if (url == null || !url.toLowerCase().contains("_test")) {
            throw new IllegalStateException(
                    "Refusing to run: TEST_DB_URL must point at a separate database whose name contains '_test'.");
        }
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username", () -> System.getenv().getOrDefault("TEST_DB_USERNAME", "chainlance"));
        registry.add("spring.datasource.password", () -> System.getenv().getOrDefault("TEST_DB_PASSWORD", ""));
    }

    private static final String PASSWORD = "Str0ng-pass-123";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    private String register(String role) throws Exception {
        String email = "w-" + UUID.randomUUID().toString().substring(0, 12) + "@example.test";
        String body = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("email", email, "password", PASSWORD, "displayName", "W", "role", role))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("token").asText();
    }

    private JsonNode challenge(String token, String address) throws Exception {
        String body = mockMvc.perform(post("/api/wallet/challenge").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("address", address))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nonce").exists())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    private String verifyBody(String nonce, String signature) throws Exception {
        return objectMapper.writeValueAsString(Map.of("nonce", nonce, "signature", signature));
    }

    @Test
    void linkAWalletWithARealSignatureThenListIt() throws Exception {
        String token = register("FREELANCER");
        ECKeyPair key = WalletTestSupport.newKeyPair();
        String address = WalletTestSupport.addressOf(key);

        // ask with the EIP-55 checksummed form, as MetaMask displays it
        JsonNode challenge = challenge(token, Keys.toChecksumAddress(address));
        assertEquals(address, challenge.get("address").asText());
        String signature = WalletTestSupport.personalSign(key, challenge.get("message").asText());

        mockMvc.perform(post("/api/wallet/verify").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(verifyBody(challenge.get("nonce").asText(), signature)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.address").value(address));

        mockMvc.perform(get("/api/wallet").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].address").value(address));
    }

    @Test
    void aChallengeCanOnlyBeUsedOnce() throws Exception {
        String token = register("CLIENT");
        ECKeyPair key = WalletTestSupport.newKeyPair();
        JsonNode challenge = challenge(token, WalletTestSupport.addressOf(key));
        String body = verifyBody(challenge.get("nonce").asText(),
                WalletTestSupport.personalSign(key, challenge.get("message").asText()));

        mockMvc.perform(post("/api/wallet/verify").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated());
        mockMvc.perform(post("/api/wallet/verify").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CHALLENGE_USED"));
    }

    @Test
    void aSignatureFromAnotherKeyIsRejected() throws Exception {
        String token = register("CLIENT");
        ECKeyPair claimed = WalletTestSupport.newKeyPair();
        ECKeyPair attacker = WalletTestSupport.newKeyPair();
        JsonNode challenge = challenge(token, WalletTestSupport.addressOf(claimed));

        mockMvc.perform(post("/api/wallet/verify").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(verifyBody(challenge.get("nonce").asText(),
                                WalletTestSupport.personalSign(attacker, challenge.get("message").asText()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SIGNATURE_MISMATCH"));

        mockMvc.perform(get("/api/wallet").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void anotherUserCannotUseMyChallengeEvenWithAValidSignature() throws Exception {
        String owner = register("CLIENT");
        String thief = register("FREELANCER");
        ECKeyPair key = WalletTestSupport.newKeyPair();
        JsonNode challenge = challenge(owner, WalletTestSupport.addressOf(key));

        mockMvc.perform(post("/api/wallet/verify").header("Authorization", "Bearer " + thief)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(verifyBody(challenge.get("nonce").asText(),
                                WalletTestSupport.personalSign(key, challenge.get("message").asText()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CHALLENGE"));
    }

    @Test
    void aWalletAlreadyLinkedToOneAccountCannotBeLinkedToAnother() throws Exception {
        String first = register("CLIENT");
        String second = register("FREELANCER");
        ECKeyPair key = WalletTestSupport.newKeyPair();
        String address = WalletTestSupport.addressOf(key);
        JsonNode challenge = challenge(first, address);
        mockMvc.perform(post("/api/wallet/verify").header("Authorization", "Bearer " + first)
                .contentType(MediaType.APPLICATION_JSON)
                .content(verifyBody(challenge.get("nonce").asText(),
                        WalletTestSupport.personalSign(key, challenge.get("message").asText())))).andExpect(status().isCreated());

        mockMvc.perform(post("/api/wallet/challenge").header("Authorization", "Bearer " + second)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("address", address))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("WALLET_ALREADY_LINKED"));
    }

    @Test
    void invalidAddressesAndMissingLoginAreRejected() throws Exception {
        String token = register("CLIENT");

        mockMvc.perform(post("/api/wallet/challenge").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("address", "0x1234"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ADDRESS"));

        mockMvc.perform(post("/api/wallet/challenge").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("address", "0x" + "1".repeat(40)))))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/wallet")).andExpect(status().isUnauthorized());
    }
}
