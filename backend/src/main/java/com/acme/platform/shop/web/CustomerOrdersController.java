package com.acme.platform.shop.web;

import com.acme.platform.shop.application.OrderService;
import com.acme.platform.shop.web.ShopDtos.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(value = "/api/v1/orders", produces = MediaType.APPLICATION_JSON_VALUE)
public class CustomerOrdersController {
    private final OrderService service;
    public CustomerOrdersController(OrderService service) { this.service = service; }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<OrderResponse> place(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody OrderInput input) {
        var order = service.place(UUID.fromString(jwt.getSubject()), input.requestId(), input.address().toDomain(),
                input.items().stream().map(OrderItemInput::toDomain).toList());
        return ResponseEntity.created(URI.create("/api/v1/orders/" + order.header().id())).cacheControl(CacheControl.noStore()).body(OrderResponse.from(order));
    }

    @GetMapping
    public ResponseEntity<PageResponse<OrderSummaryResponse>> orders(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) @Pattern(regexp = "pending|confirmed|shipped|cancelled") String status,
            @RequestParam(defaultValue = "0") @Min(0) @Max(10000) int page,
            @RequestParam(defaultValue = "12") @Min(1) @Max(50) int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ShopDtos.orders(service.list(UUID.fromString(jwt.getSubject()), ShopDtos.status(status), page, size)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> detail(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(OrderResponse.from(service.detail(id, UUID.fromString(jwt.getSubject()))));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<OrderResponse> cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(OrderResponse.from(service.cancel(id, UUID.fromString(jwt.getSubject()))));
    }
}
