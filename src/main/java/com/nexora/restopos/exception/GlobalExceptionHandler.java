package com.nexora.restopos.exception;

import com.nexora.restopos.dto.ApiResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // `message` sigue siendo el mensaje combinado (compatibilidad con pantallas que solo
    // leen ese campo), pero `data` ahora trae el detalle por campo ({field: message}) para
    // que un formulario pueda resaltar el input exacto que falló, en vez de solo mostrar un
    // banner genérico — igual que el resto de los endpoints de la API usan `data` para su
    // payload normal, aquí lo usan para el detalle de la validación.
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.putIfAbsent(fe.getField(), fe.getDefaultMessage());
        }
        String msg = String.join(", ", fieldErrors.values());
        return ResponseEntity.badRequest().body(
                ApiResponse.<Map<String, String>>builder().success(false).message(msg).data(fieldErrors).build());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArg(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalState(IllegalStateException ex) {
        return ResponseEntity.badRequest().body(ApiResponse.error(ex.getMessage()));
    }

    // Misma forma que handleValidation (data: {campo: mensaje}) para que el frontend resalte
    // el input exacto, pero para reglas de negocio que @Valid no puede expresar (ej. un
    // código de barras repetido, o un stock que quedaría negativo) — ver FieldConflictException.
    @ExceptionHandler(FieldConflictException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleFieldConflict(FieldConflictException ex) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        fieldErrors.put(ex.getField(), ex.getMessage());
        return ResponseEntity.badRequest().body(
                ApiResponse.<Map<String, String>>builder().success(false).message(ex.getMessage()).data(fieldErrors).build());
    }

    // Spring la lanza ANTES de llegar al controller (spring.servlet.multipart.max-file-size,
    // ver application.properties) — no extiende RuntimeException, así que sin este handler
    // se cae con un error crudo del servidor en vez del mismo mensaje amigable que
    // ProductImageService/TiendaLogoService ya dan cuando sí alcanzan a validar el tamaño.
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        return ResponseEntity.badRequest().body(ApiResponse.error("El archivo no debe pesar más de 3 MB"));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadCreds(BadCredentialsException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("Credenciales inválidas"));
    }

    /** Dos personas modificaron lo mismo al mismo tiempo: gana la primera, la segunda reintenta. */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ApiResponse<Void>> handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error("Alguien más modificó esto al mismo tiempo. Actualiza la pantalla e inténtalo de nuevo."));
    }

    /** Violación de una regla única de la base (ver DbConstraintsMigration), típicamente por una carrera. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleIntegrity(DataIntegrityViolationException ex) {
        String msg = String.valueOf(ex.getMostSpecificCause().getMessage());
        String texto = msg.contains("ux_cash_cuts_un_abierto_por_usuario") ? "Ya tienes un corte de caja abierto"
                : msg.contains("ux_ordenes_una_activa_por_mesa") ? "Esa mesa ya tiene una orden abierta"
                : "La operación entra en conflicto con otro registro. Actualiza la pantalla e inténtalo de nuevo.";
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(texto));
    }

    @ExceptionHandler(ClienteNoAutenticadoException.class)
    public ResponseEntity<ApiResponse<Void>> handleClienteNoAutenticado(ClienteNoAutenticadoException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(InvalidRefreshTokenException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidRefreshToken(InvalidRefreshTokenException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error(ex.getMessage()));
    }

    // Spring Security lanza este mismo tipo de excepción en dos casos muy distintos: (a) un
    // @PreAuthorize de método rechazando la petición (mensaje fijo en inglés — "Access
    // Denied" o "Access is denied" según la ruta interna que lo dispare, sin valor para el
    // usuario) y (b) una regla de negocio en un service (ej. jerarquía de roles en
    // UserService, o "no puedes administrar esta tienda" en TenantScope) que sí construye
    // un mensaje específico en español. Se distinguen por idioma: cualquier mensaje propio
    // de esta app va siempre en español, así que un mensaje puramente en inglés (variantes
    // conocidas de Spring Security) se reemplaza por el genérico en español.
    private static final Set<String> SPRING_SECURITY_DEFAULT_MESSAGES = Set.of("access denied", "access is denied");

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        String raw = ex.getMessage();
        boolean hasBusinessMessage = raw != null && !SPRING_SECURITY_DEFAULT_MESSAGES.contains(raw.trim().toLowerCase());
        String msg = hasBusinessMessage ? raw : "Acceso denegado";
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(msg));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResponse<Void>> handleRuntime(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.error(ex.getMessage()));
    }
}
