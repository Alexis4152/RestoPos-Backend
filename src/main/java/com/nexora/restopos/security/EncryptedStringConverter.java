package com.nexora.restopos.security;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Aplica {@link CredentialEncryptionService} de forma transparente en el límite del ORM:
 * un campo con {@code @Convert(converter = EncryptedStringConverter.class)} queda cifrado en
 * la columna y se descifra solo al leerlo como entidad — así ningún punto de guardado nuevo
 * puede "olvidar" cifrar por accidente.
 *
 * <p>{@code @Component} para que Hibernate lo resuelva desde Spring y reciba {@link
 * CredentialEncryptionService} por inyección (Spring Boot 3 lo soporta de fábrica).</p>
 */
@Converter
@Component
@RequiredArgsConstructor
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    private final CredentialEncryptionService encryptionService;

    @Override
    public String convertToDatabaseColumn(String attribute) {
        return (attribute == null || attribute.isBlank()) ? attribute : encryptionService.encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        return (dbData == null || dbData.isBlank()) ? dbData : encryptionService.decrypt(dbData);
    }
}
