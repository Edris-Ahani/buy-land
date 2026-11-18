package com.acme.platform.shop.web;

import com.acme.platform.shop.application.OrderService;
import com.acme.platform.shop.web.ShopDtos.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(value = "/api/v1/admin/orders", produces = MediaType.APPLICATION_JSON_VALUE)
public class AdminOrdersController {
    private final OrderService service;
    public AdminOrdersController(OrderService service) { this.service = service; }

    @GetMapping
    public ResponseEntity<PageResponse<OrderSummaryResponse>> orders(@RequestParam(required = false) UUID userId,
            @RequestParam(required = false) @Pattern(regexp = "pending|confirmed|shipped|cancelled") String status,
            @RequestParam(defaultValue = "0") @Min(0) @Max(10000) int page,
            @RequestParam(defaultValue = "12") @Min(1) @Max(50) int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ShopDtos.orders(service.list(userId, ShopDtos.status(status), page, size)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> detail(@PathVariable UUID id) { return response(service.detail(id, null)); }

    @PostMapping("/{id}/confirm")
    public ResponseEntity<OrderResponse> confirm(@PathVariable UUID id) { return response(service.confirm(id)); }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<OrderResponse> cancel(@PathVariable UUID id) { return response(service.cancel(id, null)); }

    @PostMapping(value = "/{id}/ship", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<OrderResponse> ship(@PathVariable UUID id, @Valid @RequestBody ShippingInput input) {
        return response(service.ship(id, input.carrier(), input.trackingCode()));
    }

    private ResponseEntity<OrderResponse> response(com.acme.platform.shop.domain.ShopModels.Order order) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(OrderResponse.from(order));
    }
}
