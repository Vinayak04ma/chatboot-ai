package com.daoninhthai.aichatbot.service;

import com.daoninhthai.aichatbot.dto.request.LoginRequest;
import com.daoninhthai.aichatbot.dto.request.RegisterRequest;
import com.daoninhthai.aichatbot.dto.response.AuthResponse;
import com.daoninhthai.aichatbot.entity.User;
import com.daoninhthai.aichatbot.exception.BadRequestException;
import com.daoninhthai.aichatbot.exception.UnauthorizedException;
import com.daoninhthai.aichatbot.repository.UserRepository;
import com.daoninhthai.aichatbot.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private AuthenticationManager authenticationManager;

    @InjectMocks
    private AuthService authService;

    private User sampleUser;

    @BeforeEach
    void setUp() {
        sampleUser = User.builder()
                .id(1L)
                .email("test@example.com")
                .password("encoded_pass")
                .displayName("Test User")
                .role(User.Role.USER)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("Should successfully register a new user")
    void register_Success() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("test@example.com");
        request.setPassword("password123");
        request.setDisplayName("Test User");

        when(userRepository.existsByEmail("test@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("encoded_pass");
        when(userRepository.save(any(User.class))).thenReturn(sampleUser);
        when(jwtTokenProvider.generateAccessToken(eq(1L), eq("test@example.com"), eq("USER")))
                .thenReturn("mock-access-token");
        when(jwtTokenProvider.generateRefreshToken(eq(1L), eq("test@example.com"), eq("USER")))
                .thenReturn("mock-refresh-token");

        AuthResponse response = authService.register(request);

        assertThat(response).isNotNull();
        assertThat(response.getAccessToken()).isEqualTo("mock-access-token");
        assertThat(response.getRefreshToken()).isEqualTo("mock-refresh-token");
        assertThat(response.getUser().getEmail()).isEqualTo("test@example.com");

        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    @DisplayName("Should reject registration when email already exists")
    void register_DuplicateEmail_ThrowsBadRequest() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("test@example.com");
        request.setPassword("password123");
        request.setDisplayName("Test User");

        when(userRepository.existsByEmail("test@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already registered");

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Should login successfully with valid credentials")
    void login_Success() {
        LoginRequest request = new LoginRequest();
        request.setEmail("test@example.com");
        request.setPassword("password123");

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(null);
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(sampleUser));
        when(jwtTokenProvider.generateAccessToken(eq(1L), eq("test@example.com"), eq("USER")))
                .thenReturn("mock-access-token");
        when(jwtTokenProvider.generateRefreshToken(eq(1L), eq("test@example.com"), eq("USER")))
                .thenReturn("mock-refresh-token");

        AuthResponse response = authService.login(request);

        assertThat(response).isNotNull();
        assertThat(response.getAccessToken()).isEqualTo("mock-access-token");
        assertThat(response.getUser().getDisplayName()).isEqualTo("Test User");
    }

    @Test
    @DisplayName("Should throw exception when authentication fails")
    void login_BadCredentials_ThrowsException() {
        LoginRequest request = new LoginRequest();
        request.setEmail("test@example.com");
        request.setPassword("wrongpass");

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    @DisplayName("Should refresh token successfully with valid refresh token")
    void refreshToken_Success() {
        String token = "valid-refresh-token";

        when(jwtTokenProvider.validateToken(token)).thenReturn(true);
        when(jwtTokenProvider.getTokenType(token)).thenReturn("refresh");
        when(jwtTokenProvider.getUserIdFromToken(token)).thenReturn(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser));
        when(jwtTokenProvider.generateAccessToken(eq(1L), eq("test@example.com"), eq("USER")))
                .thenReturn("new-access-token");
        when(jwtTokenProvider.generateRefreshToken(eq(1L), eq("test@example.com"), eq("USER")))
                .thenReturn("new-refresh-token");

        AuthResponse response = authService.refreshToken(token);

        assertThat(response).isNotNull();
        assertThat(response.getAccessToken()).isEqualTo("new-access-token");
        assertThat(response.getRefreshToken()).isEqualTo("new-refresh-token");
    }

    @Test
    @DisplayName("Should reject invalid refresh token")
    void refreshToken_InvalidToken_ThrowsUnauthorized() {
        when(jwtTokenProvider.validateToken("invalid")).thenReturn(false);

        assertThatThrownBy(() -> authService.refreshToken("invalid"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Invalid refresh token");
    }
}
