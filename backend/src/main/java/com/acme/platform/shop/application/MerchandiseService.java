package com.acme.platform.shop.application;

import com.acme.platform.shop.domain.ShopFailure;
import com.acme.platform.shop.domain.ShopModels;
import com.acme.platform.shop.domain.ShopModels.*;
import java.net.URI;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class MerchandiseService {
    private final ShopStore store;
    private final Clock clock;
    public MerchandiseService(ShopStore store, Clock clock) { this.store = store; this.clock = clock; }

    public Page<Category> categories(int page, int size) {
        ShopModels.pagination(page, size);
        return store.categories(page, size);
    }

    @Transactional
    public Category createCategory(String name, String slug) {
        var category = new Category(UUID.randomUUID(), ShopModels.text(name, 1, 120), slug(slug), clock.instant());
        store.insertCategory(category);
        return category;
    }

    @Transactional
    public Category updateCategory(UUID id, String name, String slug) {
        return store.updateCategory(id, ShopModels.text(name, 1, 120), slug(slug)).orElseThrow(ShopFailure::notFound);
    }

    public Page<Product> products(UUID categoryId, String query, boolean includeInactive, int page, int size) {
        ShopModels.pagination(page, size);
        String search = query == null ? "" : ShopModels.text(query, 0, 100);
        return store.products(categoryId, search, includeInactive, page, size);
    }

    public Product product(UUID id, boolean includeInactive) {
        return store.product(id).filter(product -> includeInactive || product.active()).orElseThrow(ShopFailure::notFound);
    }

    @Transactional
    public Product createProduct(UUID categoryId, String sku, String name, String description, String imageUrl,
            long price, long stock, boolean active) {
        var now = clock.instant();
        var product = validated(UUID.randomUUID(), categoryId, sku, name, description, imageUrl, price, stock, active, 0, now, now);
        store.insertProduct(product);
        return product;
    }

    @Transactional
    public Product updateProduct(UUID id, UUID categoryId, String sku, String name, String description, String imageUrl,
            long price, long stock, boolean active, long version) {
        if (version < 0) throw new IllegalArgumentException("Invalid product version");
        Product previous = store.lockProducts(List.of(id)).stream().findFirst().orElseThrow(ShopFailure::notFound);
        if (previous.version() != version) throw ShopFailure.conflict("Product changed; reload it before editing");
        var product = validated(id, categoryId, sku, name, description, imageUrl, price, stock, active,
                version + 1, previous.createdAt(), clock.instant());
        if (!store.updateProduct(product, version)) throw ShopFailure.conflict("Product changed; reload it before editing");
        return product;
    }

    private Product validated(UUID id, UUID categoryId, String sku, String name, String description, String imageUrl,
            long price, long stock, boolean active, long version, java.time.Instant created, java.time.Instant updated) {
        if (categoryId == null || store.category(categoryId).isEmpty()) throw ShopFailure.notFound();
        if (sku == null || !sku.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,63}")) throw new IllegalArgumentException("Invalid SKU");
        if (price < 1 || price > 1_000_000_000_000L || stock < 0 || stock > 1_000_000_000L) {
            throw new IllegalArgumentException("Invalid price or stock");
        }
        return new Product(id, categoryId, sku, ShopModels.text(name, 1, 120), ShopModels.text(description, 0, 2000),
                imageUrl(imageUrl), price, stock, active, version, created, updated);
    }

    private static String imageUrl(String value) {
        String url = ShopModels.text(value, 1, 2048);
        URI parsed = URI.create(url);
        if (!"https".equals(parsed.getScheme()) || parsed.getHost() == null || parsed.getUserInfo() != null) {
            throw new IllegalArgumentException("An absolute HTTPS image URL is required");
        }
        return url;
    }

    private static String slug(String value) {
        if (value == null || value.length() > 80 || !value.matches("[a-z0-9]+(?:-[a-z0-9]+)*")) {
            throw new IllegalArgumentException("Invalid category slug");
        }
        return value;
    }
}
