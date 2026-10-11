package com.nexora.restopos.config;

import com.fasterxml.jackson.datatype.hibernate6.Hibernate6Module;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registra {@link Hibernate6Module} para que Jackson sepa serializar entidades con
 * relaciones {@code FetchType.LAZY} (ej. {@code createdBy}/{@code updatedBy}/{@code
 * deletedBy}) sin tronar al encontrarse el proxy interno que genera Hibernate para esas
 * relaciones (error típico: "Type definition error: [simple type, class
 * org.hibernate.proxy.pojo.bytebuddy...]").
 *
 * <p>Con {@code spring.jpa.open-in-view=true} la sesión de Hibernate sigue abierta durante
 * la serialización, así que la mayoría de las relaciones LAZY se resuelven solas. Para las
 * que no se hayan tocado en la sesión, {@code FORCE_LAZY_LOADING=false} le dice a Jackson
 * que las serialice como {@code null} en vez de forzar una consulta adicional (evitando
 * tanto el error del proxy como problemas de N+1 queries).</p>
 */
@Configuration
public class JacksonConfig {

    @Bean
    public Hibernate6Module hibernate6Module() {
        Hibernate6Module module = new Hibernate6Module();
        module.configure(Hibernate6Module.Feature.FORCE_LAZY_LOADING, false);
        return module;
    }
}
