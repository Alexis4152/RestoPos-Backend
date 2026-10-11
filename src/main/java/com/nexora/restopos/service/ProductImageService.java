package com.nexora.restopos.service;

import com.nexora.restopos.model.Product;
import com.nexora.restopos.model.User;
import com.nexora.restopos.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.UUID;

/**
 * Administra la subida y borrado de la foto de un producto/platillo, igual que
 * {@link TiendaLogoService} pero para {@link Product#getImageUrl()}: archivo en disco bajo
 * {@code app.uploads.dir}/products, servido públicamente vía {@code /uploads/**}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProductImageService {

    private static final Set<String> ALLOWED_TYPES = Set.of("image/png", "image/jpeg", "image/webp");
    private static final long MAX_SIZE_BYTES = 3L * 1024 * 1024; // 3 MB

    private final ProductRepository productRepository;
    private final ProductService productService;

    @Value("${app.uploads.dir}")
    private String uploadsDir;

    @Transactional
    public Product upload(Long productId, MultipartFile file, User actor) {
        Product product = productService.findById(productId, actor);
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Selecciona un archivo de imagen");
        }
        if (!ALLOWED_TYPES.contains(file.getContentType())) {
            throw new IllegalArgumentException("La imagen debe ser PNG, JPG o WEBP");
        }
        if (file.getSize() > MAX_SIZE_BYTES) {
            throw new IllegalArgumentException("La imagen no debe pesar más de 3 MB");
        }

        try {
            Path productsDir = Paths.get(uploadsDir, "products");
            Files.createDirectories(productsDir);

            String ext = extensionFor(file.getContentType());
            String filename = "product-" + productId + "-" + UUID.randomUUID() + ext;
            Path target = productsDir.resolve(filename);
            file.transferTo(target);

            deleteExistingFile(product.getImageUrl());
            product.setImageUrl("/uploads/products/" + filename);
            product.setUpdatedBy(actor);
            return productRepository.save(product);
        } catch (IOException e) {
            throw new RuntimeException("No se pudo guardar la imagen", e);
        }
    }

    @Transactional
    public Product remove(Long productId, User actor) {
        Product product = productService.findById(productId, actor);
        deleteExistingFile(product.getImageUrl());
        product.setImageUrl(null);
        product.setUpdatedBy(actor);
        return productRepository.save(product);
    }

    private void deleteExistingFile(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) return;
        try {
            Path previous = Paths.get(uploadsDir, imageUrl.replaceFirst("^/uploads/", ""));
            Files.deleteIfExists(previous);
        } catch (IOException e) {
            log.warn("No se pudo borrar la imagen anterior ({}): {}", imageUrl, e.getMessage());
        }
    }

    private String extensionFor(String contentType) {
        return switch (contentType) {
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> ".jpg";
        };
    }
}
