package com.chainlance.dto;

import java.time.Instant;

/** The exact message must be signed as-is (personal_sign). The server signs nothing and keeps no keys. */
public record ChallengeResponse(String nonce, String message, String address, Instant expiresAt) {
}
