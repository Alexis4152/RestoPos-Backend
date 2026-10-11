package com.nexora.restopos.controller;

import com.nexora.restopos.dto.ApiResponse;
import com.nexora.restopos.dto.ChangePasswordRequest;
import com.nexora.restopos.dto.ForgotPasswordRequest;
import com.nexora.restopos.dto.LoginRequest;
import com.nexora.restopos.dto.LoginResponse;
import com.nexora.restopos.dto.RefreshResponse;
import com.nexora.restopos.dto.ResetPasswordRequest;
import com.nexora.restopos.exception.InvalidRefreshTokenException;
import com.nexora.restopos.model.User;
import com.nexora.restopos.service.AuthService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Controlador de autenticación, expuesto bajo {@code /api/auth}.
 * <p>
 * No requiere permisos de sección ni de rol específico: el endpoint de login es
 * público (punto de entrada al sistema) y el de "usuario actual" solo exige que
 * la petición ya venga autenticada con un JWT válido. {@code change-password} es la
 * excepción: {@link com.nexora.restopos.config.SecurityConfig} lo marca aparte como
 * autenticado (a diferencia del resto de este prefijo, público) porque lee al actor
 * autenticado vía {@code @AuthenticationPrincipal}.
 * <p>
 * El refresh token de sesión viaja SIEMPRE como cookie httpOnly ({@code restopos_refresh_token},
 * scope {@code /api/auth}) — nunca en el body JSON, para que JS del frontend no pueda leerlo
 * (mitiga robo vía XSS). {@code login}/{@code refresh}/{@code logout} son los únicos puntos
 * que la tocan.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String REFRESH_COOKIE_NAME = "restopos_refresh_token";

    private final AuthService authService;

    @Value("${app.jwt.refresh-expiration}")
    private long refreshExpirationMs;

    @Value("${app.cookie.secure}")
    private boolean cookieSecure;

    @Value("${app.cookie.samesite}")
    private String cookieSameSite;

    /**
     * Autentica a un usuario con email y contraseña, emite el access token JWT que debe
     * usarse en el resto de las llamadas a la API (header {@code Authorization}) y adjunta
     * el refresh token nuevo como cookie httpOnly.
     *
     * @param req credenciales de acceso (email y contraseña)
     * @param response respuesta HTTP, usada para adjuntar la cookie del refresh token
     * @return datos de sesión (token y datos básicos del usuario) envueltos en {@link ApiResponse}
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest req,
                                                              HttpServletResponse response) {
        AuthService.LoginResult result = authService.login(req);
        addRefreshCookie(response, result.getRefreshToken(), refreshExpirationMs / 1000);
        return ResponseEntity.ok(ApiResponse.ok(result.getBody(), "Login exitoso"));
    }

    /**
     * Canjea el refresh token de la cookie httpOnly por un access token JWT nuevo, sin pedir
     * credenciales de nuevo — usado por el interceptor de axios del frontend cuando una
     * petición falla con 401 por access token vencido.
     *
     * @param refreshToken valor de la cookie {@code restopos_refresh_token}; ausente si nunca
     *                      hubo login o la cookie ya expiró/fue borrada
     * @return el access token JWT nuevo
     * @throws InvalidRefreshTokenException (401) si no hay cookie, o el token es inválido/venció
     */
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<RefreshResponse>> refresh(
            @CookieValue(value = REFRESH_COOKIE_NAME, required = false) String refreshToken) {
        if (refreshToken == null) {
            throw new InvalidRefreshTokenException("Sesión expirada, vuelve a iniciar sesión");
        }
        String accessToken = authService.refreshAccessToken(refreshToken);
        return ResponseEntity.ok(ApiResponse.ok(RefreshResponse.builder().token(accessToken).build(), "Token renovado"));
    }

    /**
     * Cierra la sesión: revoca el refresh token actual del lado servidor y limpia la cookie.
     * Idempotente — responde 200 incluso si ya no había cookie que revocar.
     *
     * @param refreshToken valor de la cookie {@code restopos_refresh_token}, si existe
     * @param response respuesta HTTP, usada para borrar la cookie
     */
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @CookieValue(value = REFRESH_COOKIE_NAME, required = false) String refreshToken,
            HttpServletResponse response) {
        if (refreshToken != null) {
            authService.logout(refreshToken);
        }
        addRefreshCookie(response, "", 0);
        return ResponseEntity.ok(ApiResponse.ok(null, "Sesión cerrada"));
    }

    /** Adjunta (o borra, con {@code maxAgeSeconds=0}) la cookie httpOnly del refresh token. */
    private void addRefreshCookie(HttpServletResponse response, String value, long maxAgeSeconds) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE_NAME, value)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(cookieSameSite)
                .path("/api/auth")
                .maxAge(maxAgeSeconds)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    /**
     * Devuelve la información del usuario autenticado en la sesión actual, tal
     * como fue resuelto a partir del JWT de la petición.
     *
     * @param user usuario autenticado, inyectado por Spring Security a partir del token
     */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<User>> me(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(ApiResponse.ok(user, "Usuario actual"));
    }

    /**
     * Cambia la contraseña del usuario ya autenticado (exige la actual como comprobante de
     * identidad). Es lo que usa la pantalla obligatoria de "cambia tu contraseña" cuando un
     * admin dio de alta al usuario (o le reseteó la contraseña) con una temporal generada
     * por el sistema (ver {@code LoginResponse#getMustChangePassword()}).
     *
     * @param req contraseña actual y la nueva elegida
     * @param actor usuario autenticado, inyectado a partir del JWT
     */
    @PostMapping("/change-password")
    public ResponseEntity<ApiResponse<Void>> changePassword(@Valid @RequestBody ChangePasswordRequest req,
                                                              @AuthenticationPrincipal User actor) {
        authService.changePassword(actor, req.getCurrentPassword(), req.getNewPassword());
        return ResponseEntity.ok(ApiResponse.ok(null, "Contraseña actualizada"));
    }

    /**
     * Inicia el flujo de "olvidé mi contraseña". Público (sin sesión) — el mensaje de
     * respuesta es SIEMPRE el mismo genérico, exista o no ese correo en el sistema, para no
     * dejar enumerar qué correos están registrados.
     *
     * @param req correo capturado en el formulario
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest req) {
        authService.forgotPassword(req.getEmail());
        return ResponseEntity.ok(ApiResponse.ok(null, "Si el correo está registrado, te enviamos un enlace para recuperar tu contraseña"));
    }

    /**
     * Completa el flujo de "olvidé mi contraseña" con el token recibido por correo. Público
     * (sin sesión) — quien lo llama todavía no puede autenticarse, es justo lo que este
     * endpoint resuelve.
     *
     * @param req token del link y la nueva contraseña elegida
     */
    /**
     * Revisa si el token del link sigue vigente, sin usarlo. Lo llama la pantalla de
     * "Restablecer contraseña" al abrirse: con un link vencido o usado muestra el error en
     * lugar del formulario. Responde 400 con el mensaje si el token no sirve.
     */
    @GetMapping("/reset-password/validate")
    public ResponseEntity<ApiResponse<Void>> validarResetToken(@RequestParam(required = false) String token) {
        authService.validarResetToken(token);
        return ResponseEntity.ok(ApiResponse.ok(null, "Enlace válido"));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<Void>> resetPassword(@Valid @RequestBody ResetPasswordRequest req) {
        authService.resetPassword(req.getToken(), req.getNewPassword());
        return ResponseEntity.ok(ApiResponse.ok(null, "Contraseña actualizada, ya puedes iniciar sesión"));
    }
}
