package com.acme.platform.shop.domain;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class ShopModels {
    private ShopModels() {}

    public record Page<T>(List<T> items, int page, int size, long totalElements, int totalPages) {
        public Page { items = List.copyOf(items); }
    }

    public record Category(UUID id, String name, String slug, Instant createdAt) {}

    public record Product(UUID id, UUID categoryId, String sku, String name, String description, String imageUrl,
            long price, long stock, boolean active, long version, Instant createdAt, Instant updatedAt) {}

    public record Address(String recipientName, String phoneNumber, String province, String city,
            String addressLine, String postalCode) {
        public Address {
            recipientName = text(recipientName, 1, 160);
            phoneNumber = text(phoneNumber, 11, 13);
            if (phoneNumber.matches("09[0-9]{9}")) phoneNumber = "+98" + phoneNumber.substring(1);
            if (!phoneNumber.matches("\\+989[0-9]{9}")) throw new IllegalArgumentException("Invalid delivery phone");
            province = text(province, 1, 80);
            city = text(city, 1, 80);
            addressLine = text(addressLine, 10, 500);
            if (postalCode == null || !postalCode.matches("[0-9]{10}")) throw new IllegalArgumentException("Invalid postal code");
        }
        @Override public String toString() { return "Address[REDACTED]"; }
    }

    public record RequestedLine(UUID productId, int quantity) {
        public RequestedLine {
            if (productId == null || quantity < 1 || quantity > 100) throw new IllegalArgumentException("Invalid order line");
        }
    }

    public record OrderLine(UUID productId, String sku, String name, long unitPrice, int quantity, long lineTotal) {}

    public enum OrderStatus {
        PENDING, CONFIRMED, SHIPPED, CANCELLED;
        public String value() { return name().toLowerCase(Locale.ROOT); }
        public static OrderStatus fromValue(String value) {
            for (OrderStatus status : values()) if (status.value().equals(value)) return status;
            throw new IllegalArgumentException("Invalid order status");
        }
    }

    public record OrderHeader(UUID id, UUID userId, UUID requestId, OrderStatus status, long total,
            Address address, String carrier, String trackingCode, Instant createdAt, Instant updatedAt) {
        @Override public String toString() { return "OrderHeader[id=" + id + ", status=" + status + "]"; }
    }

    public record Order(OrderHeader header, List<OrderLine> items) {
        public Order { items = List.copyOf(items); }
        @Override public String toString() { return "Order[id=" + header.id() + "]"; }
    }

    public static String text(String value, int min, int max) {
        if (value == null || value.strip().length() < min || value.length() > max || value.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("Invalid shop input");
        }
        return value.strip();
    }

    public static void pagination(int page, int size) {
        if (page < 0 || page > 10000 || size < 1 || size > 50) throw new IllegalArgumentException("Invalid pagination");
    }
}
