package com.acme.platform.shop.web;

import com.acme.platform.shop.domain.ShopModels.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ShopDtos {
    private ShopDtos() {}

    public record PageResponse<T>(List<T> items, int page, int size, long totalElements, int totalPages) {}
    public record CategoryInput(@NotBlank @Size(max = 120) String name,
            @NotBlank @Size(max = 80) @Pattern(regexp = "[a-z0-9]+(?:-[a-z0-9]+)*") String slug) {}
    public record CategoryResponse(UUID id, String name, String slug, Instant createdAt) {
        static CategoryResponse from(Category category) { return new CategoryResponse(category.id(), category.name(), category.slug(), category.createdAt()); }
    }

    public record ProductInput(@NotNull UUID categoryId,
            @NotBlank @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9_-]{0,63}") String sku,
            @NotBlank @Size(max = 120) String name, @NotNull @Size(max = 2000) String description,
            @NotBlank @Size(max = 2048) String imageUrl, @NotNull @Min(1) @Max(1_000_000_000_000L) Long price,
            @NotNull @Min(0) @Max(1_000_000_000L) Long stock, @NotNull Boolean active) {}

    public record ProductUpdate(@NotNull UUID categoryId,
            @NotBlank @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9_-]{0,63}") String sku,
            @NotBlank @Size(max = 120) String name, @NotNull @Size(max = 2000) String description,
            @NotBlank @Size(max = 2048) String imageUrl, @NotNull @Min(1) @Max(1_000_000_000_000L) Long price,
            @NotNull @Min(0) @Max(1_000_000_000L) Long stock, @NotNull Boolean active, @NotNull @Min(0) Long version) {}

    public record ProductResponse(UUID id, UUID categoryId, String sku, String name, String description, String imageUrl,
            long price, String currency, long stock, boolean active, long version, Instant createdAt, Instant updatedAt) {
        static ProductResponse from(Product product) {
            return new ProductResponse(product.id(), product.categoryId(), product.sku(), product.name(), product.description(),
                    product.imageUrl(), product.price(), "IRR", product.stock(), product.active(), product.version(), product.createdAt(), product.updatedAt());
        }
    }

    public record AddressInput(@NotBlank @Size(max = 160) String recipientName,
            @NotBlank @Pattern(regexp = "(?:09[0-9]{9}|\\+989[0-9]{9})") String phoneNumber,
            @NotBlank @Size(max = 80) String province, @NotBlank @Size(max = 80) String city,
            @NotBlank @Size(min = 10, max = 500) String addressLine, @NotNull @Pattern(regexp = "[0-9]{10}") String postalCode) {
        Address toDomain() { return new Address(recipientName, phoneNumber, province, city, addressLine, postalCode); }
        @Override public String toString() { return "AddressInput[REDACTED]"; }
    }

    public record OrderItemInput(@NotNull UUID productId, @NotNull @Min(1) @Max(100) Integer quantity) {
        RequestedLine toDomain() { return new RequestedLine(productId, quantity); }
    }
    public record OrderInput(@NotNull UUID requestId, @NotNull @Valid AddressInput address,
            @NotEmpty @Size(max = 50) List<@NotNull @Valid OrderItemInput> items) {
        @Override public String toString() { return "OrderInput[REDACTED]"; }
    }
    public record ShippingInput(@NotBlank @Size(max = 80) String carrier, @NotBlank @Size(max = 120) String trackingCode) {}

    public record AddressResponse(String recipientName, String phoneNumber, String province, String city, String addressLine, String postalCode) {
        static AddressResponse from(Address address) {
            return new AddressResponse(address.recipientName(), address.phoneNumber(), address.province(), address.city(), address.addressLine(), address.postalCode());
        }
        @Override public String toString() { return "AddressResponse[REDACTED]"; }
    }
    public record OrderLineResponse(UUID productId, String sku, String name, long unitPrice, int quantity, long lineTotal) {
        static OrderLineResponse from(OrderLine line) { return new OrderLineResponse(line.productId(), line.sku(), line.name(), line.unitPrice(), line.quantity(), line.lineTotal()); }
    }
    public record OrderSummaryResponse(UUID id, UUID userId, UUID requestId, String status, long total, String currency,
            String carrier, String trackingCode, Instant createdAt, Instant updatedAt) {
        static OrderSummaryResponse from(OrderHeader header) {
            return new OrderSummaryResponse(header.id(), header.userId(), header.requestId(), header.status().value(), header.total(), "IRR",
                    header.carrier(), header.trackingCode(), header.createdAt(), header.updatedAt());
        }
    }
    public record OrderResponse(UUID id, UUID userId, UUID requestId, String status, long total, String currency,
            String carrier, String trackingCode, Instant createdAt, Instant updatedAt, AddressResponse address, List<OrderLineResponse> items) {
        static OrderResponse from(Order order) {
            var header = order.header();
            return new OrderResponse(header.id(), header.userId(), header.requestId(), header.status().value(), header.total(), "IRR",
                    header.carrier(), header.trackingCode(), header.createdAt(), header.updatedAt(), AddressResponse.from(header.address()),
                    order.items().stream().map(OrderLineResponse::from).toList());
        }
        @Override public String toString() { return "OrderResponse[id=" + id + "]"; }
    }
    static PageResponse<CategoryResponse> categories(Page<Category> page) {
        return new PageResponse<>(page.items().stream().map(CategoryResponse::from).toList(), page.page(), page.size(), page.totalElements(), page.totalPages());
    }
    static PageResponse<ProductResponse> products(Page<Product> page) {
        return new PageResponse<>(page.items().stream().map(ProductResponse::from).toList(), page.page(), page.size(), page.totalElements(), page.totalPages());
    }
    static PageResponse<OrderSummaryResponse> orders(Page<OrderHeader> page) {
        return new PageResponse<>(page.items().stream().map(OrderSummaryResponse::from).toList(), page.page(), page.size(), page.totalElements(), page.totalPages());
    }
    static OrderStatus status(String value) { return value == null ? null : OrderStatus.fromValue(value); }
}
