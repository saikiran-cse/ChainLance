package com.chainlance.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chainlance.dto.AuthResponse;
import com.chainlance.dto.LoginRequest;
import com.chainlance.dto.RegisterRequest;
import com.chainlance.entity.User;
import com.chainlance.entity.enums.Role;
import com.chainlance.repository.UserRepository;
import com.chainlance.security.JwtService;
import com.chainlance.web.ApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

/** Pure unit test with mocks: no Spring context, no database. */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository users;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private JwtService jwtService;

    private AuthService service() {
        return new AuthService(users, passwordEncoder, authenticationManager, jwtService);
    }

    @Test
    void registerNormalizesEmailHashesPasswordAndIssuesToken() {
        when(users.existsByEmail("alice@example.test")).thenReturn(false);
        when(passwordEncoder.encode("Str0ng-pass-123")).thenReturn("HASHED");
        when(users.saveAndFlush(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 7L);
            return saved;
        });
        when(jwtService.issue(eq(7L), eq("alice@example.test"), eq(Role.CLIENT))).thenReturn("the-token");
        when(jwtService.expiresInSeconds()).thenReturn(7200L);

        AuthResponse response = service().register(
                new RegisterRequest("  Alice@Example.TEST ", "Str0ng-pass-123", "  Alice  ", Role.CLIENT));

        assertEquals("the-token", response.token());
        assertEquals("Bearer", response.tokenType());
        assertEquals(7200L, response.expiresInSeconds());
        assertEquals("alice@example.test", response.user().email());

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(users).saveAndFlush(captor.capture());
        User saved = captor.getValue();
        assertEquals("HASHED", saved.getPasswordHash());
        assertEquals("Alice", saved.getDisplayName());
        assertEquals(Role.CLIENT, saved.getRole());
        assertEquals(true, saved.isEnabled());
    }

    @Test
    void registerRejectsAdminRole() {
        ApiException ex = assertThrows(ApiException.class, () -> service().register(
                new RegisterRequest("a@b.test", "Str0ng-pass-123", "Eve", Role.ADMIN)));

        assertEquals("ROLE_NOT_ALLOWED", ex.getCode());
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void registerRejectsDuplicateEmail() {
        when(users.existsByEmail("a@b.test")).thenReturn(true);

        ApiException ex = assertThrows(ApiException.class, () -> service().register(
                new RegisterRequest("A@B.test", "Str0ng-pass-123", "Bob", Role.FREELANCER)));

        assertEquals("EMAIL_TAKEN", ex.getCode());
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void registerRejectsPasswordOver72Bytes() {
        String tooLong = "p".repeat(73);

        ApiException ex = assertThrows(ApiException.class, () -> service().register(
                new RegisterRequest("a@b.test", tooLong, "Bob", Role.CLIENT)));

        assertEquals("PASSWORD_TOO_LONG", ex.getCode());
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void loginGivesTheSameGenericErrorForAnyAuthenticationFailure() {
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("bad"));

        ApiException ex = assertThrows(ApiException.class,
                () -> service().login(new LoginRequest("a@b.test", "whatever")));

        assertEquals("INVALID_CREDENTIALS", ex.getCode());
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatus());
        verify(jwtService, never()).issue(any(), anyString(), any());
    }

    @Test
    void loginRejectsOverlongPasswordWithoutCallingTheAuthenticationManager() {
        ApiException ex = assertThrows(ApiException.class,
                () -> service().login(new LoginRequest("a@b.test", "p".repeat(100))));

        assertEquals("INVALID_CREDENTIALS", ex.getCode());
        verify(authenticationManager, never()).authenticate(any());
    }
}
