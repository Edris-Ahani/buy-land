package com.acme.platform.shop.web;

import com.acme.platform.shop.application.MerchandiseService;
import com.acme.platform.shop.web.ShopDtos.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(value = "/api/v1/admin", produces = MediaType.APPLICATION_JSON_VALUE)
public class AdminShopController {
    private final MerchandiseService service;
    public AdminShopController(MerchandiseService service) { this.service = service; }

    @PostMapping(value = "/categories", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryResponse createCategory(@Valid @RequestBody CategoryInput input) {
        return CategoryResponse.from(service.createCategory(input.name(), input.slug()));
    }

    @PutMapping(value = "/categories/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public CategoryResponse updateCategory(@PathVariable UUID id, @Valid @RequestBody CategoryInput input) {
        return CategoryResponse.from(service.updateCategory(id, input.name(), input.slug()));
    }

    @GetMapping("/products")
    public PageResponse<ProductResponse> products(@RequestParam(required = false) UUID categoryId,
            @RequestParam(defaultValue = "") @Size(max = 100) String q,
            @RequestParam(defaultValue = "0") @Min(0) @Max(10000) int page,
            @RequestParam(defaultValue = "12") @Min(1) @Max(50) int size) {
        return ShopDtos.products(service.products(categoryId, q, true, page, size));
    }

    @GetMapping("/products/{id}")
    public ProductResponse product(@PathVariable UUID id) { return ProductResponse.from(service.product(id, true)); }

    @PostMapping(value = "/products", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse createProduct(@Valid @RequestBody ProductInput input) {
        return ProductResponse.from(service.createProduct(input.categoryId(), input.sku(), input.name(), input.description(), input.imageUrl(),
                input.price(), input.stock(), input.active()));
    }

    @PutMapping(value = "/products/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ProductResponse updateProduct(@PathVariable UUID id, @Valid @RequestBody ProductUpdate input) {
        return ProductResponse.from(service.updateProduct(id, input.categoryId(), input.sku(), input.name(), input.description(), input.imageUrl(),
                input.price(), input.stock(), input.active(), input.version()));
    }
}
