package com.acme.platform.shop.application;

import com.acme.platform.shop.domain.ShopFailure;
import com.acme.platform.shop.domain.ShopModels;
import com.acme.platform.shop.domain.ShopModels.*;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class OrderService {
    private final ShopStore store;
    private final Clock clock;
    public OrderService(ShopStore store, Clock clock) { this.store = store; this.clock = clock; }

    @Transactional
    public Order place(UUID userId, UUID requestId, Address address, List<RequestedLine> requested) {
        if (userId == null || requestId == null || address == null || requested == null || requested.isEmpty() || requested.size() > 50) {
            throw new IllegalArgumentException("Invalid order");
        }
        var lines = requested.stream().sorted(Comparator.comparing(RequestedLine::productId)).toList();
        if (lines.stream().map(RequestedLine::productId).distinct().count() != lines.size()) {
            throw new IllegalArgumentException("Duplicate product lines are not allowed");
        }
        var now = clock.instant();
        var header = new OrderHeader(UUID.randomUUID(), userId, requestId, OrderStatus.PENDING, 0,
                address, null, null, now, now);
        if (!store.insertOrder(header)) {
            var previous = store.orderByRequest(userId, requestId).orElseThrow(ShopFailure::notFound);
            var existingLines = store.orderLines(previous.id());
            var previousRequest = existingLines.stream().map(line -> new RequestedLine(line.productId(), line.quantity())).toList();
            if (!previous.address().equals(address) || !previousRequest.equals(lines)) {
                throw ShopFailure.conflict("The requestId was already used for a different order");
            }
            return new Order(previous, existingLines);
        }
        var products = new HashMap<UUID, Product>();
        store.lockProducts(lines.stream().map(RequestedLine::productId).toList()).forEach(product -> products.put(product.id(), product));
        var snapshots = new ArrayList<OrderLine>();
        long total = 0;
        for (var line : lines) {
            var product = products.get(line.productId());
            if (product == null || !product.active() || product.stock() < line.quantity()) {
                throw ShopFailure.conflict("One or more products are unavailable or have insufficient stock");
            }
            long lineTotal = Math.multiplyExact(product.price(), line.quantity());
            total = Math.addExact(total, lineTotal);
            snapshots.add(new OrderLine(product.id(), product.sku(), product.name(), product.price(), line.quantity(), lineTotal));
            store.changeStock(product.id(), -line.quantity(), now);
        }
        store.insertOrderLines(header.id(), snapshots);
        store.setOrderTotal(header.id(), total);
        return detail(header.id(), userId);
    }

    public Page<OrderHeader> list(UUID userId, OrderStatus status, int page, int size) {
        ShopModels.pagination(page, size);
        return store.orders(userId, status, page, size);
    }

    public Order detail(UUID id, UUID ownerId) {
        var header = visible(id, ownerId, false);
        return new Order(header, store.orderLines(id));
    }

    @Transactional
    public Order confirm(UUID id) {
        var header = visible(id, null, true);
        if (header.status() != OrderStatus.CONFIRMED) {
            if (header.status() != OrderStatus.PENDING) throw ShopFailure.conflict("Only pending orders can be confirmed");
            store.setOrderStatus(id, OrderStatus.CONFIRMED, null, null, clock.instant());
        }
        return detail(id, null);
    }

    @Transactional
    public Order ship(UUID id, String carrier, String trackingCode) {
        String deliveryCarrier = ShopModels.text(carrier, 1, 80);
        String tracking = ShopModels.text(trackingCode, 1, 120);
        var header = visible(id, null, true);
        if (header.status() == OrderStatus.SHIPPED && deliveryCarrier.equals(header.carrier()) && tracking.equals(header.trackingCode())) {
            return detail(id, null);
        }
        if (header.status() != OrderStatus.CONFIRMED) throw ShopFailure.conflict("Only confirmed orders can be shipped");
        store.setOrderStatus(id, OrderStatus.SHIPPED, deliveryCarrier, tracking, clock.instant());
        return detail(id, null);
    }

    @Transactional
    public Order cancel(UUID id, UUID ownerId) {
        var header = visible(id, ownerId, true);
        if (header.status() == OrderStatus.CANCELLED) return detail(id, ownerId);
        if (header.status() == OrderStatus.SHIPPED || (ownerId != null && header.status() != OrderStatus.PENDING)) {
            throw ShopFailure.conflict("This order can no longer be cancelled by the caller");
        }
        var lines = store.orderLines(id);
        store.lockProducts(lines.stream().map(OrderLine::productId).toList());
        var now = clock.instant();
        for (var line : lines) store.changeStock(line.productId(), line.quantity(), now);
        store.setOrderStatus(id, OrderStatus.CANCELLED, null, null, now);
        return detail(id, ownerId);
    }

    private OrderHeader visible(UUID id, UUID ownerId, boolean lock) {
        return store.order(id, lock).filter(header -> ownerId == null || ownerId.equals(header.userId())).orElseThrow(ShopFailure::notFound);
    }
}
