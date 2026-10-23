package com.chainlance.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.chainlance.config.WalletProperties;
import com.chainlance.dto.ChallengeResponse;
import com.chainlance.dto.WalletResponse;
import com.chainlance.entity.User;
import com.chainlance.entity.WalletAddress;
import com.chainlance.entity.WalletChallenge;
import com.chainlance.repository.UserRepository;
import com.chainlance.repository.WalletAddressRepository;
import com.chainlance.repository.WalletChallengeRepository;
import com.chainlance.wallet.WalletTestSupport;
import com.chainlance.web.ApiException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.web3j.crypto.ECKeyPair;

/** Pure unit test with mocks: no Spring context, no database, throwaway keys only. */
@ExtendWith(MockitoExtension.class)
class WalletServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-23T10:00:00Z");
    private static final String NONCE = "0123456789abcdef0123456789abcdef";
    private static final String MESSAGE = "ChainLance test message\nNonce: " + NONCE;

    @Mock private WalletChallengeRepository challenges;
    @Mock private WalletAddressRepository wallets;
    @Mock private UserRepository users;

    private WalletService service() {
        return new WalletService(challenges, wallets, users, new WalletProperties(5), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static User user(long id) {
        User user = new User();
        ReflectionTestUtils.setField(user, "id", id);
        user.setEmail("u" + id + "@example.test");
        return user;
    }

    private static WalletChallenge challenge(User owner, String address, Instant expiresAt) {
        WalletChallenge c = new WalletChallenge();
        ReflectionTestUtils.setField(c, "id", 77L);
        c.setUser(owner);
        c.setAddress(address);
        c.setNonce(NONCE);
        c.setMessage(MESSAGE);
        c.setExpiresAt(expiresAt);
        return c;
    }

    private static void assertApi(ApiException ex, HttpStatus status, String code) {
        assertEquals(code, ex.getCode());
        assertEquals(status, ex.getStatus());
    }

    // ------------------------------------------------------------------ createChallenge

    @Test
    void createChallengeStoresANormalizedAddressRandomNonceAndFiveMinuteExpiry() {
        when(wallets.findByAddress("0x5aaeb6053f3e94c9b9a09f33669435e7ef1beaed")).thenReturn(Optional.empty());
        when(challenges.countByUserIdAndUsedAtIsNullAndExpiresAtAfter(1L, NOW)).thenReturn(0L);
        when(users.findById(1L)).thenReturn(Optional.of(user(1L)));
        when(challenges.save(any(WalletChallenge.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // input is the EIP-55 checksummed form with surrounding spaces
        ChallengeResponse response = service().createChallenge(1L, " 0x5aAeb6053F3E94C9b9A09f33669435E7Ef1BeAed ");

        assertEquals("0x5aaeb6053f3e94c9b9a09f33669435e7ef1beaed", response.address());
        assertEquals(32, response.nonce().length());
        assertEquals(NOW.plusSeconds(300), response.expiresAt());
        assertTrue(response.message().contains("Nonce: " + response.nonce()));
        assertTrue(response.message().contains("Wallet: " + response.address()));
        assertTrue(response.message().contains("Account ID: 1"));
        assertTrue(response.message().length() < 500, "must fit the VARCHAR(500) column");

        ArgumentCaptor<WalletChallenge> saved = ArgumentCaptor.forClass(WalletChallenge.class);
        verify(challenges).save(saved.capture());
        assertEquals(response.message(), saved.getValue().getMessage());
        assertEquals(response.nonce(), saved.getValue().getNonce());
    }

    @Test
    void createChallengeNeverReusesANonce() {
        when(wallets.findByAddress(any())).thenReturn(Optional.empty());
        when(challenges.countByUserIdAndUsedAtIsNullAndExpiresAtAfter(any(), any())).thenReturn(0L);
        when(users.findById(1L)).thenReturn(Optional.of(user(1L)));
        when(challenges.save(any(WalletChallenge.class))).thenAnswer(invocation -> invocation.getArgument(0));

        String address = "0x5aaeb6053f3e94c9b9a09f33669435e7ef1beaed";
        String first = service().createChallenge(1L, address).nonce();
        String second = service().createChallenge(1L, address).nonce();

        assertNotNull(first);
        assertTrue(!first.equals(second));
    }

    @Test
    void createChallengeRejectsAnInvalidAddressBeforeTouchingTheDatabase() {
        ApiException ex = assertThrows(ApiException.class, () -> service().createChallenge(1L, "0x1234"));

        assertApi(ex, HttpStatus.BAD_REQUEST, "INVALID_ADDRESS");
        verifyNoInteractions(challenges, wallets, users);
    }

    @Test
    void createChallengeRejectsAnAddressAlreadyLinkedToAnAccount() {
        when(wallets.findByAddress("0x5aaeb6053f3e94c9b9a09f33669435e7ef1beaed")).thenReturn(Optional.of(new WalletAddress()));

        ApiException ex = assertThrows(ApiException.class,
                () -> service().createChallenge(1L, "0x5aaeb6053f3e94c9b9a09f33669435e7ef1beaed"));

        assertApi(ex, HttpStatus.CONFLICT, "WALLET_ALREADY_LINKED");
        verify(challenges, never()).save(any());
    }

    @Test
    void createChallengeLimitsHowManyChallengesCanBeOpenAtOnce() {
        when(wallets.findByAddress(any())).thenReturn(Optional.empty());
        when(challenges.countByUserIdAndUsedAtIsNullAndExpiresAtAfter(1L, NOW)).thenReturn(10L);

        ApiException ex = assertThrows(ApiException.class,
                () -> service().createChallenge(1L, "0x5aaeb6053f3e94c9b9a09f33669435e7ef1beaed"));

        assertApi(ex, HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_CHALLENGES");
        verify(challenges, never()).save(any());
    }

    // ------------------------------------------------------------------ verify

    @Test
    void verifyLinksTheWalletWhenTheSignatureMatchesAndConsumesTheChallenge() throws Exception {
        ECKeyPair key = WalletTestSupport.newKeyPair();
        String address = WalletTestSupport.addressOf(key);
        User owner = user(1L);
        when(challenges.findByNonce(NONCE)).thenReturn(Optional.of(challenge(owner, address, NOW.plusSeconds(60))));
        when(wallets.findByAddress(address)).thenReturn(Optional.empty());
        when(wallets.findByUserIdOrderByCreatedAtAsc(1L)).thenReturn(List.of());
        when(challenges.markUsedIfValid(eq(77L), eq(NOW))).thenReturn(1);
        when(users.findById(1L)).thenReturn(Optional.of(owner));
        when(wallets.saveAndFlush(any(WalletAddress.class))).thenAnswer(invocation -> invocation.getArgument(0));

        WalletResponse response = service().verify(1L, NONCE, WalletTestSupport.personalSign(key, MESSAGE));

        assertEquals(address, response.address());
        assertEquals(NOW, response.verifiedAt());
        ArgumentCaptor<WalletAddress> saved = ArgumentCaptor.forClass(WalletAddress.class);
        verify(wallets).saveAndFlush(saved.capture());
        assertEquals(address, saved.getValue().getAddress());
        assertEquals(owner, saved.getValue().getUser());
    }

    @Test
    void verifyRejectsASignatureMadeByADifferentWalletAndDoesNotConsumeTheChallenge() throws Exception {
        ECKeyPair claimed = WalletTestSupport.newKeyPair();
        ECKeyPair attacker = WalletTestSupport.newKeyPair();
        when(challenges.findByNonce(NONCE)).thenReturn(
                Optional.of(challenge(user(1L), WalletTestSupport.addressOf(claimed), NOW.plusSeconds(60))));

        ApiException ex = assertThrows(ApiException.class,
                () -> service().verify(1L, NONCE, WalletTestSupport.personalSign(attacker, MESSAGE)));

        assertApi(ex, HttpStatus.BAD_REQUEST, "SIGNATURE_MISMATCH");
        verify(challenges, never()).markUsedIfValid(any(), any());
        verify(wallets, never()).saveAndFlush(any());
    }

    @Test
    void verifyRejectsASignatureOverDifferentText() throws Exception {
        ECKeyPair key = WalletTestSupport.newKeyPair();
        when(challenges.findByNonce(NONCE)).thenReturn(
                Optional.of(challenge(user(1L), WalletTestSupport.addressOf(key), NOW.plusSeconds(60))));

        ApiException ex = assertThrows(ApiException.class,
                () -> service().verify(1L, NONCE, WalletTestSupport.personalSign(key, "some other text")));

        assertApi(ex, HttpStatus.BAD_REQUEST, "SIGNATURE_MISMATCH");
    }

    @Test
    void verifyRejectsAMalformedSignature() {
        when(challenges.findByNonce(NONCE)).thenReturn(
                Optional.of(challenge(user(1L), "0x5aaeb6053f3e94c9b9a09f33669435e7ef1beaed", NOW.plusSeconds(60))));

        ApiException ex = assertThrows(ApiException.class, () -> service().verify(1L, NONCE, "0x1234"));

        assertApi(ex, HttpStatus.BAD_REQUEST, "SIGNATURE_INVALID");
    }

    @Test
    void verifyGivesTheSameAnswerForAnUnknownNonceAndSomeoneElsesChallenge() {
        when(challenges.findByNonce("nope")).thenReturn(Optional.empty());
        ApiException unknown = assertThrows(ApiException.class, () -> service().verify(1L, "nope", "0x"));
        assertApi(unknown, HttpStatus.BAD_REQUEST, "INVALID_CHALLENGE");

        when(challenges.findByNonce(NONCE)).thenReturn(
                Optional.of(challenge(user(2L), "0x5aaeb6053f3e94c9b9a09f33669435e7ef1beaed", NOW.plusSeconds(60))));
        ApiException foreign = assertThrows(ApiException.class, () -> service().verify(1L, NONCE, "0x"));
        assertApi(foreign, HttpStatus.BAD_REQUEST, "INVALID_CHALLENGE");
        assertEquals(unknown.getMessage(), foreign.getMessage());
    }

    @Test
    void verifyRejectsAnExpiredChallenge() {
        when(challenges.findByNonce(NONCE)).thenReturn(
                Optional.of(challenge(user(1L), "0x5aaeb6053f3e94c9b9a09f33669435e7ef1beaed", NOW)));

        ApiException ex = assertThrows(ApiException.class, () -> service().verify(1L, NONCE, "0x"));

        assertApi(ex, HttpStatus.BAD_REQUEST, "CHALLENGE_EXPIRED");
    }

    @Test
    void verifyRejectsAChallengeThatWasAlreadyUsed() {
        WalletChallenge used = challenge(user(1L), "0x5aaeb6053f3e94c9b9a09f33669435e7ef1beaed", NOW.plusSeconds(60));
        used.setUsedAt(NOW.minusSeconds(5));
        when(challenges.findByNonce(NONCE)).thenReturn(Optional.of(used));

        ApiException ex = assertThrows(ApiException.class, () -> service().verify(1L, NONCE, "0x"));

        assertApi(ex, HttpStatus.BAD_REQUEST, "CHALLENGE_USED");
    }

    @Test
    void verifyFailsIfAnotherRequestConsumedTheChallengeFirst() throws Exception {
        ECKeyPair key = WalletTestSupport.newKeyPair();
        String address = WalletTestSupport.addressOf(key);
        when(challenges.findByNonce(NONCE)).thenReturn(Optional.of(challenge(user(1L), address, NOW.plusSeconds(60))));
        when(wallets.findByAddress(address)).thenReturn(Optional.empty());
        when(wallets.findByUserIdOrderByCreatedAtAsc(1L)).thenReturn(List.of());
        when(challenges.markUsedIfValid(eq(77L), eq(NOW))).thenReturn(0); // lost the race

        ApiException ex = assertThrows(ApiException.class,
                () -> service().verify(1L, NONCE, WalletTestSupport.personalSign(key, MESSAGE)));

        assertApi(ex, HttpStatus.BAD_REQUEST, "CHALLENGE_USED");
        verify(wallets, never()).saveAndFlush(any());
    }

    @Test
    void verifyRejectsAWalletThatAnotherAccountLinkedInTheMeantime() throws Exception {
        ECKeyPair key = WalletTestSupport.newKeyPair();
        String address = WalletTestSupport.addressOf(key);
        when(challenges.findByNonce(NONCE)).thenReturn(Optional.of(challenge(user(1L), address, NOW.plusSeconds(60))));
        when(wallets.findByAddress(address)).thenReturn(Optional.of(new WalletAddress()));

        ApiException ex = assertThrows(ApiException.class,
                () -> service().verify(1L, NONCE, WalletTestSupport.personalSign(key, MESSAGE)));

        assertApi(ex, HttpStatus.CONFLICT, "WALLET_ALREADY_LINKED");
        verify(challenges, never()).markUsedIfValid(any(), any());
    }

    @Test
    void verifyEnforcesTheWalletLimitPerUser() throws Exception {
        ECKeyPair key = WalletTestSupport.newKeyPair();
        String address = WalletTestSupport.addressOf(key);
        when(challenges.findByNonce(NONCE)).thenReturn(Optional.of(challenge(user(1L), address, NOW.plusSeconds(60))));
        when(wallets.findByAddress(address)).thenReturn(Optional.empty());
        when(wallets.findByUserIdOrderByCreatedAtAsc(1L)).thenReturn(
                List.of(new WalletAddress(), new WalletAddress(), new WalletAddress(), new WalletAddress(), new WalletAddress()));

        ApiException ex = assertThrows(ApiException.class,
                () -> service().verify(1L, NONCE, WalletTestSupport.personalSign(key, MESSAGE)));

        assertApi(ex, HttpStatus.CONFLICT, "WALLET_LIMIT");
        verify(challenges, never()).markUsedIfValid(any(), any());
    }
}
