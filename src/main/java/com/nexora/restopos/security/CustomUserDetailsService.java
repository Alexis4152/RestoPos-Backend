package com.nexora.restopos.security;

import com.nexora.restopos.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Implementación de {@link UserDetailsService} usada por Spring Security para cargar el
 * usuario autenticado. El "username" del sistema es el email: {@code User} implementa
 * {@link UserDetails} directamente, así que no existe una clase adaptadora aparte. Este
 * servicio lo usan tanto el login (a través del {@code AuthenticationManager} configurado en
 * {@code SecurityConfig}) como {@link JwtAuthFilter} en cada request autenticado por token.
 */
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    /**
     * Busca al usuario por email. Lanza {@link UsernameNotFoundException} si no existe, tal
     * como lo exige el contrato de {@link UserDetailsService}.
     *
     * @param email correo del usuario, usado como identificador único de login
     * @return el {@code User} correspondiente (implementa {@link UserDetails})
     */
    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado: " + email));
    }
}
