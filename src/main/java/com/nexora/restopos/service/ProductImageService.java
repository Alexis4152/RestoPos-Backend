package com.nexora.restopos.service;

import com.nexora.restopos.model.Product;
import com.nexora.restopos.model.User;
import com.nexora.restopos.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Administra la foto de un producto/platillo ({@link Product#getImageUrl()}): archivo en disco
 * bajo {@code app.uploads.dir}/products, servido públicamente vía {@code /uploads/**}. La
 * validación (tipo real por contenido, 3 MB) y el guardado viven en {@link ImageStorageService}.
 */
@Service
@RequiredArgsConstructor
public class ProductImageService {

    private final ProductRepository productRepository;
    private final ProductService productService;
    private final ImageStorageService imageStorage;

    @Transactional
    public Product upload(Long productId, MultipartFile file, User actor) {
        Product product = productService.findById(productId, actor);
        String ruta = imageStorage.guardar(file, "products", "product-" + productId, "La imagen");
        imageStorage.borrar(product.getImageUrl());
        product.setImageUrl(ruta);
        product.setUpdatedBy(actor);
        return productRepository.save(product);
    }

    @Transactional
    public Product remove(Long productId, User actor) {
        Product product = productService.findById(productId, actor);
        imageStorage.borrar(product.getImageUrl());
        product.setImageUrl(null);
        product.setUpdatedBy(actor);
        return productRepository.save(product);
    }
}
