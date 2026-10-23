package com.chainlance.service;

import com.chainlance.config.WalletProperties;
import com.chainlance.dto.ChallengeResponse;
import com.chainlance.dto.WalletResponse;
import com.chainlance.entity.User;
import com.chainlance.entity.WalletAddress;
import com.chainlance.entity.WalletChallenge;
import com.chainlance.repository.UserRepository;
import com.chainlance.repository.WalletAddressRepository;
import com.chainlance.repository.WalletChallengeRepository;
import com.chainlance.wallet.EthereumAddress;
import com.chainlance.wallet.PersonalSignVerifier;
import com.chainlance.web.ApiException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Links a MetaMask wallet to a logged-in user by proving key ownership:
 * the server issues a single-use, short-lived challenge, the user signs it in MetaMask (no gas, no transaction),
 * and the server recovers the signer from the signature. The server never sees or stores any private key.
 */
@Service
public class WalletService {

    static final int MAX_OPEN_CHALLENGES = 10;
    static final int MAX_WALLETS_PER_USER = 5;

    private final WalletChallengeRepository challenges;
    private final WalletAddressRepository wallets;
    private final UserRepository users;
    private final WalletProperties properties;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public WalletService(WalletChallengeRepository challenges, WalletAddressRepository wallets, UserRepository users,
                         WalletProperties properties, Clock clock) {
        this.challenges = challenges;
        this.wallets = wallets;
        this.users = users;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public ChallengeResponse createChallenge(Long userId, String rawAddress) {
        String address = normalizeOrReject(rawAddress);
        if (wallets.findByAddress(address).isPresent()) {
            throw walletTaken();
        }
        Instant now = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        if (challenges.countByUserIdAndUsedAtIsNullAndExpiresAtAfter(userId, now) >= MAX_OPEN_CHALLENGES) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_CHALLENGES",
                    "Too many pending wallet challenges. Wait a few minutes and try again.");
        }
        User user = users.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found"));

        String nonce = newNonce();
        Instant expiresAt = now.plus(Duration.ofMinutes(properties.challengeTtlMinutes()));

        WalletChallenge challenge = new WalletChallenge();
        challenge.setUser(user);
        challenge.setAddress(address);
        challenge.setNonce(nonce);
        challenge.setMessage(buildMessage(userId, address, nonce, now, expiresAt));
        challenge.setExpiresAt(expiresAt);
        challenges.save(challenge);

        return new ChallengeResponse(nonce, challenge.getMessage(), address, expiresAt);
    }

    @Transactional
    public WalletResponse verify(Long userId, String nonce, String signature) {
        WalletChallenge challenge = challenges.findByNonce(nonce.trim()).orElse(null);
        // Same answer for "unknown" and "someone else's": a nonce must not reveal who owns it.
        if (challenge == null || !challenge.getUser().getId().equals(userId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_CHALLENGE", "Unknown or invalid challenge");
        }
        Instant now = clock.instant();
        if (challenge.getUsedAt() != null) {
            throw challengeUsed();
        }
        if (!challenge.getExpiresAt().isAfter(now)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CHALLENGE_EXPIRED",
                    "The challenge has expired. Request a new one.");
        }

        // The signed text is the one stored on the server, never text supplied by the client.
        String recovered;
        try {
            recovered = PersonalSignVerifier.recoverAddress(challenge.getMessage(), signature);
        } catch (IllegalArgumentException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SIGNATURE_INVALID", ex.getMessage());
        }
        if (!recovered.equals(challenge.getAddress())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SIGNATURE_MISMATCH",
                    "The signature was not made by the wallet being linked");
        }

        if (wallets.findByAddress(challenge.getAddress()).isPresent()) {
            throw walletTaken();
        }
        if (wallets.findByUserIdOrderByCreatedAtAsc(userId).size() >= MAX_WALLETS_PER_USER) {
            throw new ApiException(HttpStatus.CONFLICT, "WALLET_LIMIT",
                    "You can link at most " + MAX_WALLETS_PER_USER + " wallets");
        }

        // Atomic single use: only one of any concurrent attempts can win this update.
        Long challengeId = challenge.getId();
        String address = challenge.getAddress();
        if (challenges.markUsedIfValid(challengeId, now) != 1) {
            throw challengeUsed();
        }

        User user = users.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found"));
        WalletAddress wallet = new WalletAddress();
        wallet.setUser(user);
        wallet.setAddress(address);
        wallet.setVerifiedAt(now);
        try {
            wallets.saveAndFlush(wallet);
        } catch (DataIntegrityViolationException ex) {
            throw walletTaken(); // two users raced to link the same address; the unique index decided
        }
        return WalletResponse.from(wallet);
    }

    @Transactional(readOnly = true)
    public List<WalletResponse> list(Long userId) {
        return wallets.findByUserIdOrderByCreatedAtAsc(userId).stream().map(WalletResponse::from).toList();
    }

    private String newNonce() {
        byte[] bytes = new byte[16];
        random.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes); // 32 hex characters, 128 bits
    }

    private static String buildMessage(Long userId, String address, String nonce, Instant issuedAt, Instant expiresAt) {
        return "ChainLance wallet verification\n\n"
                + "Sign this message to prove you control the wallet below and link it to your ChainLance account.\n"
                + "This is not a transaction, costs no gas and gives no one access to your funds.\n\n"
                + "Account ID: " + userId + "\n"
                + "Wallet: " + address + "\n"
                + "Nonce: " + nonce + "\n"
                + "Issued At: " + issuedAt + "\n"
                + "Expires At: " + expiresAt;
    }

    private static String normalizeOrReject(String rawAddress) {
        try {
            return EthereumAddress.normalize(rawAddress);
        } catch (IllegalArgumentException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ADDRESS", ex.getMessage());
        }
    }

    private static ApiException walletTaken() {
        return new ApiException(HttpStatus.CONFLICT, "WALLET_ALREADY_LINKED",
                "This wallet is already linked to an account");
    }

    private static ApiException challengeUsed() {
        return new ApiException(HttpStatus.BAD_REQUEST, "CHALLENGE_USED",
                "This challenge has already been used. Request a new one.");
    }
}
