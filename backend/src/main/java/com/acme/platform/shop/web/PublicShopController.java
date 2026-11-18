package com.acme.platform.shop.web;

import com.acme.platform.shop.application.MerchandiseService;
import com.acme.platform.shop.web.ShopDtos.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(value = "/api/v1", produces = MediaType.APPLICATION_JSON_VALUE)
public class PublicShopController {
    private final MerchandiseService service;
    public PublicShopController(MerchandiseService service) { this.service = service; }

    @GetMapping("/categories")
    public PageResponse<CategoryResponse> categories(@RequestParam(defaultValue = "0") @Min(0) @Max(10000) int page,
            @RequestParam(defaultValue = "12") @Min(1) @Max(50) int size) {
        return ShopDtos.categories(service.categories(page, size));
    }

    @GetMapping("/products")
    public PageResponse<ProductResponse> products(@RequestParam(required = false) UUID categoryId,
            @RequestParam(defaultValue = "") @Size(max = 100) String q,
            @RequestParam(defaultValue = "0") @Min(0) @Max(10000) int page,
            @RequestParam(defaultValue = "12") @Min(1) @Max(50) int size) {
        return ShopDtos.products(service.products(categoryId, q, false, page, size));
    }

    @GetMapping("/products/{id}")
    public ProductResponse product(@PathVariable UUID id) { return ProductResponse.from(service.product(id, false)); }
}
