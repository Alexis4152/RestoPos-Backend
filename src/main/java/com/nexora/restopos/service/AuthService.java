package com.nexora.restopos.service;

import com.nexora.restopos.dto.LoginRequest;
import com.nexora.restopos.dto.LoginResponse;
import com.nexora.restopos.exception.InvalidRefreshTokenException;
import com.nexora.restopos.model.PasswordResetToken;
import com.nexora.restopos.model.RefreshToken;
import com.nexora.restopos.model.User;
import com.nexora.restopos.repository.PasswordResetTokenRepository;
import com.nexora.restopos.repository.RefreshTokenRepository;
import com.nexora.restopos.repository.UserRepository;
import com.nexora.restopos.security.JwtTokenProvider;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;

/**
 * Servicio de autenticación del sistema.
 *
 * <p>Valida credenciales contra Spring Security, emite el access token JWT (vida corta,
 * 30 min) que el resto de la API espera en el header {@code Authorization} junto con un
 * refresh token opaco (8h, revocable, ver {@link #refreshAccessToken(String)} y
 * {@link #logout(String)}), y expone al usuario autenticado (usado por el endpoint "quién
 * soy" del frontend tras el login o al refrescar la sesión).</p>
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final com.nexora.restopos.repository.TiendaInfoRepository tiendaInfoRepository;
    private final EmailService emailService;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${app.jwt.refresh-expiration}")
    private long refreshExpirationMs;

    // Vigencia del link de "olvidé mi contraseña" — igual que el resto de los tokens opacos de
    // esta clase, no configurable por properties a propósito (no hay necesidad real de cambiarlo).
    private static final long RESET_TOKEN_MINUTES = 30;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    /** Resultado interno de {@link #login}: separa el body JSON de siempre (nunca debe
     *  llevar el refresh token) del valor crudo que {@code AuthController} usa para armar
     *  la cookie httpOnly — así el refresh token nunca queda expuesto a JS del frontend. */
    @Getter @AllArgsConstructor
    public static class LoginResult {
        private final LoginResponse body;
        private final String refreshToken;
    }

    /**
     * Autentica al usuario con correo y contraseña, genera su access token JWT y un refresh
     * token nuevo (persistido, vigente {@code app.jwt.refresh-expiration}).
     *
     * <p>Delega la validación de credenciales en el {@link AuthenticationManager} de
     * Spring Security (que a su vez usa el {@code UserDetailsService} respaldado por
     * {@link UserRepository}). La respuesta incluye el rol y las secciones ({@link
     * com.nexora.restopos.model.AppSection}) habilitadas para ese usuario, de modo que el
     * frontend pueda armar el menú sin pedirlas por separado, así como la tienda a la que
     * pertenece (null para usuarios de plataforma tipo SUPER_ADMIN).</p>
     *
     * @param req credenciales de acceso (correo y contraseña en texto plano)
     * @return el body de sesión de siempre más el refresh token crudo (ver {@link LoginResult})
     * @throws org.springframework.security.core.AuthenticationException si las credenciales son inválidas
     */
    @Transactional
    public LoginResult login(LoginRequest req) {
        Authentication auth = authManager.authenticate(
                new UsernamePasswordAuthenticationToken(req.getEmail(), req.getPassword())
        );
        User user = (User) auth.getPrincipal();
        String accessToken = jwtTokenProvider.generateAccessToken(user);
        String refreshToken = issueRefreshToken(user);
        LoginResponse body = LoginResponse.builder()
                .token(accessToken)
                .userId(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .role(user.getRole().getName())
                .sections(user.getRole().getSections().stream().map(Enum::name).toList())
                .tienda(user.getTienda())
                .mustChangePassword(Boolean.TRUE.equals(user.getMustChangePassword()))
                .webSlug(user.getTienda() == null ? null : tiendaInfoRepository.findByTiendaId(user.getTienda().getId())
                        .filter(i -> Boolean.TRUE.equals(i.getWebEnabled()))
                        .map(com.nexora.restopos.model.TiendaInfo::getPublicSlug)
                        .orElse(null))
                .build();
        return new LoginResult(body, refreshToken);
    }

    /**
     * Canjea un refresh token válido por un access token JWT nuevo, sin pedir credenciales
     * de nuevo — es lo que {@code POST /api/auth/refresh} usa para renovar la sesión de
     * forma transparente cuando el access token (vida corta, 30 min) expira.
     *
     * <p>El refresh token es reusable dentro de su ventana de vigencia (no rota en cada
     * uso): varias peticiones en paralelo con el access token vencido pueden refrescar sin
     * invalidarse entre sí.</p>
     *
     * @param rawToken valor crudo del refresh token, tal como viaja en la cookie httpOnly
     * @return un access token JWT nuevo para el usuario dueño del refresh token
     * @throws InvalidRefreshTokenException si el token no existe, está revocado o ya venció
     */
    @Transactional
    public String refreshAccessToken(String rawToken) {
        RefreshToken refreshToken = refreshTokenRepository.findByToken(rawToken)
                .orElseThrow(() -> new InvalidRefreshTokenException("Sesión expirada, vuelve a iniciar sesión"));
        if (Boolean.TRUE.equals(refreshToken.getRevoked()) || refreshToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new InvalidRefreshTokenException("Sesión expirada, vuelve a iniciar sesión");
        }
        return jwtTokenProvider.generateAccessToken(refreshToken.getUser());
    }

    /**
     * Revoca puntualmente el refresh token dado — usado por {@code POST /api/auth/logout}.
     * No afecta otras sesiones/dispositivos del mismo usuario (a diferencia de
     * {@link RefreshTokenRepository#revokeAllForUser}, usado al cambiar contraseña).
     *
     * @param rawToken valor crudo del refresh token a revocar; si no existe, no hace nada
     *                  (logout es idempotente, ver {@code AuthController#logout})
     */
    @Transactional
    public void logout(String rawToken) {
        refreshTokenRepository.findByToken(rawToken).ifPresent(rt -> {
            rt.setRevoked(true);
            refreshTokenRepository.save(rt);
        });
    }

    /** Crea y persiste un refresh token nuevo para el usuario dado, vigente
     *  {@code app.jwt.refresh-expiration} desde ahora, y devuelve su valor crudo. */
    private String issueRefreshToken(User user) {
        RefreshToken refreshToken = RefreshToken.builder()
                .token(generateSecureToken())
                .user(user)
                .expiresAt(LocalDateTime.now().plusSeconds(refreshExpirationMs / 1000))
                .build();
        refreshTokenRepository.save(refreshToken);
        return refreshToken.getToken();
    }

    /** Genera un token aleatorio criptográficamente seguro, codificado en base64 URL-safe,
     *  usado como valor opaco impredecible del refresh token. */
    private String generateSecureToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * Recupera el usuario autenticado a partir del correo contenido en el JWT.
     *
     * @param email correo del usuario, tal como viene en el token
     * @return el usuario correspondiente
     * @throws IllegalArgumentException si no existe un usuario con ese correo
     */
    public User me(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));
    }

    /**
     * Cambia la contraseña del usuario ya autenticado, exigiendo la actual como
     * comprobante de identidad. Es lo que usa la pantalla obligatoria de "cambia tu
     * contraseña" cuando un admin dio de alta al usuario (o le reseteó la contraseña) con
     * una temporal generada por el sistema (ver {@link com.nexora.restopos.model.User#getMustChangePassword()}),
     * pero cualquier usuario puede usarlo también para cambiarla por gusto propio.
     *
     * <p>También revoca todos los refresh tokens activos del usuario (ver
     * {@link RefreshTokenRepository#revokeAllForUser}) — cierra cualquier otra sesión abierta
     * con la contraseña anterior.</p>
     *
     * @param actor usuario autenticado que quiere cambiar su propia contraseña
     * @param currentPassword contraseña actual, para verificar identidad
     * @param newPassword nueva contraseña elegida
     * @throws IllegalArgumentException si la contraseña actual no coincide
     */
    @Transactional
    public void changePassword(User actor, String currentPassword, String newPassword) {
        if (!passwordEncoder.matches(currentPassword, actor.getPassword())) {
            throw new IllegalArgumentException("La contraseña actual no es correcta");
        }
        actor.setPassword(passwordEncoder.encode(newPassword));
        actor.setMustChangePassword(false);
        userRepository.save(actor);
        refreshTokenRepository.revokeAllForUser(actor);
    }

    /**
     * Inicia el flujo de "olvidé mi contraseña": si el correo pertenece a un usuario activo,
     * genera un token de un solo uso (vigente {@link #RESET_TOKEN_MINUTES} minutos) y le manda
     * el link por correo.
     *
     * <p>Deliberadamente NO distingue en su resultado si el correo existía o no (el controller
     * siempre responde el mismo mensaje genérico) — evita que alguien pueda usar este endpoint
     * para enumerar qué correos están registrados en el sistema. Al pedir un link nuevo, se
     * invalidan todos los tokens sin usar previos del mismo usuario ({@link
     * PasswordResetTokenRepository#invalidateAllForUser}): solo el correo más reciente sirve.</p>
     *
     * @param email correo capturado en el formulario de "olvidé mi contraseña"
     */
    @Transactional
    public void forgotPassword(String email) {
        userRepository.findByEmail(email)
                .filter(user -> Boolean.TRUE.equals(user.getIsActive()))
                .ifPresent(user -> {
                    passwordResetTokenRepository.invalidateAllForUser(user);
                    PasswordResetToken resetToken = PasswordResetToken.builder()
                            .token(generateSecureToken())
                            .user(user)
                            .expiresAt(LocalDateTime.now().plusMinutes(RESET_TOKEN_MINUTES))
                            .build();
                    passwordResetTokenRepository.save(resetToken);
                    String resetLink = frontendUrl + "/reset-password?token=" + resetToken.getToken();
                    emailService.sendPasswordResetEmail(user, resetLink);
                });
    }

    /**
     * Completa el flujo de "olvidé mi contraseña": valida el token recibido en el link del
     * correo, actualiza la contraseña, marca el token como usado (no puede reutilizarse) y
     * revoca todas las sesiones activas del usuario (ver {@link
     * RefreshTokenRepository#revokeAllForUser}) — cierra cualquier sesión abierta con la
     * contraseña anterior.
     *
     * @param token valor del token, tal como viene en el link del correo
     * @param newPassword nueva contraseña elegida
     * @throws IllegalArgumentException si el token no existe, ya se usó, o ya venció
     */
    @Transactional
    public void resetPassword(String token, String newPassword) {
        PasswordResetToken resetToken = resetTokenVigente(token);
        User user = resetToken.getUser();
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        resetToken.setUsed(true);
        passwordResetTokenRepository.save(resetToken);
        refreshTokenRepository.revokeAllForUser(user);
    }

    /**
     * Revisa el token del link sin consumirlo, para que la pantalla de "Restablecer contraseña"
     * avise de un link vencido o usado antes de mostrar el formulario.
     *
     * @throws IllegalArgumentException si el token no existe, ya se usó, o ya venció
     */
    @Transactional(readOnly = true)
    public void validarResetToken(String token) {
        resetTokenVigente(token);
    }

    /** Token de recuperación existente, sin usar y sin vencer. Mismo mensaje en todos los casos. */
    private PasswordResetToken resetTokenVigente(String token) {
        PasswordResetToken resetToken = token == null ? null : passwordResetTokenRepository.findByToken(token).orElse(null);
        if (resetToken == null || Boolean.TRUE.equals(resetToken.getUsed())
                || resetToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("El enlace no es válido o ya venció, solicita uno nuevo");
        }
        return resetToken;
    }
}
