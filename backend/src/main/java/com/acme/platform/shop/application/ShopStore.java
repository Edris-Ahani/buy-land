package com.acme.platform.shop.application;

import com.acme.platform.shop.domain.ShopModels.*;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShopStore {
    Page<Category> categories(int page, int size);
    Optional<Category> category(UUID id);
    void insertCategory(Category category);
    Optional<Category> updateCategory(UUID id, String name, String slug);
    Page<Product> products(UUID categoryId, String query, boolean includeInactive, int page, int size);
    Optional<Product> product(UUID id);
    List<Product> lockProducts(List<UUID> ids);
    void insertProduct(Product product);
    boolean updateProduct(Product product, long expectedVersion);
    void changeStock(UUID id, long difference, Instant now);
    boolean insertOrder(OrderHeader header);
    Optional<OrderHeader> orderByRequest(UUID userId, UUID requestId);
    Optional<OrderHeader> order(UUID id, boolean lock);
    List<OrderLine> orderLines(UUID orderId);
    void insertOrderLines(UUID orderId, List<OrderLine> lines);
    void setOrderTotal(UUID id, long total);
    void setOrderStatus(UUID id, OrderStatus status, String carrier, String trackingCode, Instant now);
    Page<OrderHeader> orders(UUID userId, OrderStatus status, int page, int size);
}
