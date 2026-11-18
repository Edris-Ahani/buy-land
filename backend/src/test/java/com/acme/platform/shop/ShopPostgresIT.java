package com.acme.platform.shop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import com.acme.platform.auth.application.AuthService;
import com.acme.platform.shop.application.MerchandiseService;
import com.acme.platform.shop.application.OrderService;
import com.acme.platform.shop.domain.ShopFailure;
import com.acme.platform.shop.domain.ShopModels.*;
import com.acme.platform.support.OpenApiContract;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Import(ShopPostgresIT.Containers.class)
class ShopPostgresIT {
    @Autowired private MockMvc mvc;
    @Autowired private MerchandiseService merchandise;
    @Autowired private OrderService orders;
    @Autowired private AuthService auth;
    @Autowired private JdbcClient jdbc;
    @Value("${platform.auth.bootstrap-admin.password}") private String adminPassword;
    private final JsonMapper mapper = JsonMapper.builder().build();
    private static final Address ADDRESS = new Address("Test Customer", "09120000001", "Tehran", "Tehran", "Example street, building 12", "1234567890");

    @Test
    void completeHttpWorkflowDocumentsEveryNewShopOperation() throws Exception {
        String admin = admin();
        var customer = customer();
        String token = customer.accessToken();
        String slug = "supplies-" + UUID.randomUUID();
        var category = request(post("/api/v1/admin/categories"), "/api/v1/admin/categories", "post", admin,
                Map.of("name", "Office supplies", "slug", slug), 201);
        UUID categoryId = UUID.fromString(category.path("id").asText());
        request(put("/api/v1/admin/categories/" + categoryId), "/api/v1/admin/categories/{id}", "put", admin,
                Map.of("name", "Updated supplies", "slug", slug), 200);
        var categories = request(get("/api/v1/categories"), "/api/v1/categories", "get", null, null, 200);
        assertThat(categories.path("items").toString()).contains(categoryId.toString());
        var productInput = productInput(categoryId, "SKU-" + UUID.randomUUID(), 250000, 5, true);
        var product = request(post("/api/v1/admin/products"), "/api/v1/admin/products", "post", admin, productInput, 201);
        UUID productId = UUID.fromString(product.path("id").asText());
        request(get("/api/v1/products/" + productId), "/api/v1/products/{id}", "get", null, null, 200);
        request(get("/api/v1/admin/products/" + productId), "/api/v1/admin/products/{id}", "get", admin, null, 200);
        var listing = request(get("/api/v1/products").param("categoryId", categoryId.toString()).param("q", "notEbook"),
                "/api/v1/products", "get", null, null, 200);
        assertThat(listing.path("totalElements").asInt()).isEqualTo(1);
        request(get("/api/v1/admin/products"), "/api/v1/admin/products", "get", admin, null, 200);
        var update = new java.util.HashMap<>(productInput);
        update.put("version", 0);
        request(put("/api/v1/admin/products/" + productId), "/api/v1/admin/products/{id}", "put", admin, update, 200);
        UUID requestId = UUID.randomUUID();
        var input = orderInput(requestId, List.of(new RequestedLine(productId, 2)));
        input.put("total", 1);
        input.put("userId", UUID.randomUUID());
        var placed = request(post("/api/v1/orders"), "/api/v1/orders", "post", token, input, 201);
        UUID orderId = UUID.fromString(placed.path("id").asText());
        assertThat(placed.path("total").asLong()).isEqualTo(500000);
        assertThat(placed.path("userId").asText()).isEqualTo(customer.user().id().toString());
        assertThat(placed.path("address").path("phoneNumber").asText()).isEqualTo("+989120000001");
        request(get("/api/v1/orders"), "/api/v1/orders", "get", token, null, 200);
        request(get("/api/v1/orders/" + orderId), "/api/v1/orders/{id}", "get", token, null, 200);
        request(get("/api/v1/admin/orders").param("userId", customer.user().id().toString()).param("status", "pending"),
                "/api/v1/admin/orders", "get", admin, null, 200);
        request(get("/api/v1/admin/orders/" + orderId), "/api/v1/admin/orders/{id}", "get", admin, null, 200);
        request(post("/api/v1/admin/orders/" + orderId + "/confirm"), "/api/v1/admin/orders/{id}/confirm", "post", admin, null, 200);
        request(post("/api/v1/orders/" + orderId + "/cancel"), "/api/v1/orders/{id}/cancel", "post", token, null, 409);
        var shipping = Map.of("carrier", "Manual carrier", "trackingCode", "TRACK-001");
        request(post("/api/v1/admin/orders/" + orderId + "/ship"), "/api/v1/admin/orders/{id}/ship", "post", admin, shipping, 200);
        request(post("/api/v1/admin/orders/" + orderId + "/ship"), "/api/v1/admin/orders/{id}/ship", "post", admin, shipping, 200);
        request(post("/api/v1/admin/orders/" + orderId + "/cancel"), "/api/v1/admin/orders/{id}/cancel", "post", admin, null, 409);
        var pending = orders.place(customer.user().id(), UUID.randomUUID(), ADDRESS, List.of(new RequestedLine(productId, 1)));
        request(post("/api/v1/orders/" + pending.header().id() + "/cancel"), "/api/v1/orders/{id}/cancel", "post", token, null, 200);
        request(post("/api/v1/admin/orders/" + pending.header().id() + "/cancel"), "/api/v1/admin/orders/{id}/cancel", "post", admin, null, 200);
        assertThat(merchandise.product(productId, true).stock()).isEqualTo(3);
    }

    @Test
    void ownershipAndRolesAreEnforcedWithRealSignedTokens() throws Exception {
        var owner = customer();
        var stranger = customer();
        var product = product(2, true);
        var order = orders.place(owner.user().id(), UUID.randomUUID(), ADDRESS, List.of(new RequestedLine(product.id(), 1)));
        request(get("/api/v1/orders/" + order.header().id()), "/api/v1/orders/{id}", "get", null, null, 401);
        request(get("/api/v1/orders/" + order.header().id()), "/api/v1/orders/{id}", "get", stranger.accessToken(), null, 404);
        request(post("/api/v1/orders/" + order.header().id() + "/cancel"), "/api/v1/orders/{id}/cancel", "post", stranger.accessToken(), null, 404);
        var page = request(get("/api/v1/orders").param("userId", owner.user().id().toString()), "/api/v1/orders", "get", stranger.accessToken(), null, 200);
        assertThat(page.path("totalElements").asInt()).isZero();
        request(get("/api/v1/admin/orders"), "/api/v1/admin/orders", "get", owner.accessToken(), null, 403);
        request(post("/api/v1/admin/products"), "/api/v1/admin/products", "post", owner.accessToken(), productInput(product.categoryId(), "DENIED", 1, 1, true), 403);
        request(put("/api/v1/admin/products/" + product.id()), "/api/v1/admin/products/{id}", "put", owner.accessToken(), Map.of(), 403);
        request(post("/api/v1/orders"), "/api/v1/orders", "post", admin(), orderInput(UUID.randomUUID(), List.of(new RequestedLine(product.id(), 1))), 403);
        assertThat(merchandise.product(product.id(), true).stock()).isEqualTo(1);
    }

    @Test
    void archivesFiltersAndUniqueIdentifiersAreConsistent() throws Exception {
        String admin = admin();
        var product = product(3, false);
        request(get("/api/v1/products/" + product.id()), "/api/v1/products/{id}", "get", null, null, 404);
        var publicPage = request(get("/api/v1/products").param("categoryId", product.categoryId().toString()), "/api/v1/products", "get", null, null, 200);
        assertThat(publicPage.path("totalElements").asInt()).isZero();
        var adminPage = request(get("/api/v1/admin/products").param("categoryId", product.categoryId().toString()), "/api/v1/admin/products", "get", admin, null, 200);
        assertThat(adminPage.path("totalElements").asInt()).isEqualTo(1);
        request(post("/api/v1/admin/products"), "/api/v1/admin/products", "post", admin,
                productInput(product.categoryId(), product.sku(), 1, 0, true), 409);
        var category = merchandise.categories(0, 50).items().stream().filter(item -> item.id().equals(product.categoryId())).findFirst().orElseThrow();
        request(post("/api/v1/admin/categories"), "/api/v1/admin/categories", "post", admin, Map.of("name", "Duplicate", "slug", category.slug()), 409);
        var other = product(1, true);
        request(put("/api/v1/admin/categories/" + other.categoryId()), "/api/v1/admin/categories/{id}", "put", admin,
                Map.of("name", "Duplicate update", "slug", category.slug()), 409);
        var duplicateSku = productInput(other.categoryId(), product.sku(), 1, 1, true);
        duplicateSku.put("version", other.version());
        request(put("/api/v1/admin/products/" + other.id()), "/api/v1/admin/products/{id}", "put", admin, duplicateSku, 409);
        assertThat(merchandise.product(other.id(), true).sku()).isEqualTo(other.sku());
        request(put("/api/v1/admin/categories/" + UUID.randomUUID()), "/api/v1/admin/categories/{id}", "put", admin, Map.of("name", "Missing", "slug", "missing"), 404);
        var literal = request(get("/api/v1/products").param("q", "%"), "/api/v1/products", "get", null, null, 200);
        assertThat(literal.path("items").size()).isZero();
    }

    @Test
    void failuresRollBackTheWholeOrderAndEveryStockChange() {
        var customer = customer();
        var available = product(3, true);
        var empty = product(0, true);
        UUID request = UUID.randomUUID();
        assertThatThrownBy(() -> orders.place(customer.user().id(), request, ADDRESS,
                List.of(new RequestedLine(available.id(), 1), new RequestedLine(empty.id(), 1))))
                .isInstanceOf(ShopFailure.class);
        assertThat(merchandise.product(available.id(), true).stock()).isEqualTo(3);
        assertThat(jdbc.sql("SELECT count(*) FROM shop_orders WHERE user_id = :user AND request_id = :request")
                .param("user", customer.user().id()).param("request", request).query(Long.class).single()).isZero();
        assertThatThrownBy(() -> orders.place(customer.user().id(), UUID.randomUUID(), ADDRESS, List.of(new RequestedLine(UUID.randomUUID(), 1))))
                .isInstanceOf(ShopFailure.class);
        var archived = product(1, false);
        assertThatThrownBy(() -> orders.place(customer.user().id(), UUID.randomUUID(), ADDRESS, List.of(new RequestedLine(archived.id(), 1))))
                .isInstanceOf(ShopFailure.class);
    }

    @Test
    void idempotencyAndCancellationCannotDeductOrRestoreStockTwice() {
        var customer = customer();
        var product = product(5, true);
        UUID request = UUID.randomUUID();
        var lines = List.of(new RequestedLine(product.id(), 2));
        var placed = orders.place(customer.user().id(), request, ADDRESS, lines);
        assertThat(orders.place(customer.user().id(), request, ADDRESS, lines).header().id()).isEqualTo(placed.header().id());
        assertThatThrownBy(() -> orders.place(customer.user().id(), request, ADDRESS, List.of(new RequestedLine(product.id(), 1))))
                .isInstanceOf(ShopFailure.class);
        var changedAddress = new Address("Another Customer", ADDRESS.phoneNumber(), ADDRESS.province(), ADDRESS.city(), ADDRESS.addressLine(), ADDRESS.postalCode());
        assertThatThrownBy(() -> orders.place(customer.user().id(), request, changedAddress, lines)).isInstanceOf(ShopFailure.class);
        assertThat(merchandise.product(product.id(), true).stock()).isEqualTo(3);
        orders.cancel(placed.header().id(), customer.user().id());
        orders.cancel(placed.header().id(), customer.user().id());
        assertThat(merchandise.product(product.id(), true).stock()).isEqualTo(5);
        assertThat(orders.place(customer.user().id(), request, ADDRESS, lines).header().status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(merchandise.product(product.id(), true).stock()).isEqualTo(5);
    }

    @Test
    void productUpdatesCannotOverwriteConcurrentStockAndOrderSnapshotsStayImmutable() {
        var customer = customer();
        var product = product(5, true);
        var order = orders.place(customer.user().id(), UUID.randomUUID(), ADDRESS, List.of(new RequestedLine(product.id(), 2)));
        assertThatThrownBy(() -> merchandise.updateProduct(product.id(), product.categoryId(), product.sku(), "Changed", "", product.imageUrl(), 999, 5, true, product.version()))
                .isInstanceOf(ShopFailure.class);
        var current = merchandise.product(product.id(), true);
        merchandise.updateProduct(current.id(), current.categoryId(), current.sku() + "-NEW", "Changed name", "Changed", current.imageUrl(), 999, current.stock(), false, current.version());
        var snapshot = orders.detail(order.header().id(), customer.user().id());
        assertThat(snapshot.header().total()).isEqualTo(500000);
        assertThat(snapshot.items().getFirst().name()).isEqualTo("Notebook");
        assertThat(snapshot.items().getFirst().sku()).isEqualTo(product.sku());
        orders.cancel(order.header().id(), customer.user().id());
        assertThat(merchandise.product(product.id(), true).stock()).isEqualTo(5);
    }

    @Test
    void adminCancellationOfConfirmedOrdersAndInvalidTransitionsAreSafe() {
        var customer = customer();
        var product = product(3, true);
        var order = orders.place(customer.user().id(), UUID.randomUUID(), ADDRESS, List.of(new RequestedLine(product.id(), 1)));
        assertThatThrownBy(() -> orders.ship(order.header().id(), "Carrier", "TRACK-1")).isInstanceOf(ShopFailure.class);
        orders.confirm(order.header().id());
        orders.confirm(order.header().id());
        assertThatThrownBy(() -> orders.cancel(order.header().id(), customer.user().id())).isInstanceOf(ShopFailure.class);
        orders.cancel(order.header().id(), null);
        orders.cancel(order.header().id(), null);
        assertThat(merchandise.product(product.id(), true).stock()).isEqualTo(3);
        assertThatThrownBy(() -> orders.confirm(order.header().id())).isInstanceOf(ShopFailure.class);
    }

    @Test
    void concurrentCustomersCannotBothBuyTheLastUnit() throws Exception {
        var first = customer();
        var second = customer();
        var product = product(1, true);
        var results = concurrently(List.of(
                () -> attempt(first.user().id(), UUID.randomUUID(), List.of(new RequestedLine(product.id(), 1))),
                () -> attempt(second.user().id(), UUID.randomUUID(), List.of(new RequestedLine(product.id(), 1)))));
        assertThat(results).containsExactlyInAnyOrder("created", "conflict");
        assertThat(merchandise.product(product.id(), true).stock()).isZero();
    }

    @Test
    void concurrentIdenticalRequestsCreateOnlyOneOrder() throws Exception {
        var customer = customer();
        var product = product(2, true);
        UUID request = UUID.randomUUID();
        Callable<Order> place = () -> orders.place(customer.user().id(), request, ADDRESS, List.of(new RequestedLine(product.id(), 1)));
        var results = concurrently(List.of(place, place));
        assertThat(results.get(0).header().id()).isEqualTo(results.get(1).header().id());
        assertThat(merchandise.product(product.id(), true).stock()).isEqualTo(1);
    }

    @Test
    void concurrentCancellationRestoresStockOnce() throws Exception {
        var customer = customer();
        var product = product(1, true);
        var order = orders.place(customer.user().id(), UUID.randomUUID(), ADDRESS, List.of(new RequestedLine(product.id(), 1)));
        Callable<Order> cancel = () -> orders.cancel(order.header().id(), customer.user().id());
        concurrently(List.of(cancel, cancel));
        assertThat(merchandise.product(product.id(), true).stock()).isEqualTo(1);
        assertThat(merchandise.product(product.id(), true).version()).isEqualTo(2);
    }

    @Test
    void oppositeInputOrderLocksMultipleProductsWithoutDeadlock() throws Exception {
        var first = customer();
        var second = customer();
        var a = product(2, true);
        var b = product(2, true);
        var results = concurrently(List.of(
                () -> orders.place(first.user().id(), UUID.randomUUID(), ADDRESS, List.of(new RequestedLine(a.id(), 1), new RequestedLine(b.id(), 1))),
                () -> orders.place(second.user().id(), UUID.randomUUID(), ADDRESS, List.of(new RequestedLine(b.id(), 1), new RequestedLine(a.id(), 1)))));
        assertThat(results).hasSize(2);
        assertThat(merchandise.product(a.id(), true).stock()).isZero();
        assertThat(merchandise.product(b.id(), true).stock()).isZero();
    }

    @Test
    void invalidBodiesAndQueryLimitsUseSanitizedContractErrors() throws Exception {
        var customer = customer();
        var product = product(1, true);
        String admin = admin();
        request(get("/api/v1/products").param("size", "51"), "/api/v1/products", "get", null, null, 400);
        request(get("/api/v1/orders").param("status", "invalid"), "/api/v1/orders", "get", customer.accessToken(), null, 400);
        request(post("/api/v1/orders"), "/api/v1/orders", "post", customer.accessToken(), orderInput(UUID.randomUUID(), List.of()), 400);
        request(post("/api/v1/orders"), "/api/v1/orders", "post", customer.accessToken(),
                orderInput(UUID.randomUUID(), List.of(new RequestedLine(product.id(), 1), new RequestedLine(product.id(), 1))), 400);
        var bad = productInput(product.categoryId(), "FRACTIONAL", 1, 1, true);
        bad.put("price", 1.5);
        request(post("/api/v1/admin/products"), "/api/v1/admin/products", "post", admin, bad, 400);
        bad.put("price", -1);
        request(post("/api/v1/admin/products"), "/api/v1/admin/products", "post", admin, bad, 400);
        bad.put("price", 1);
        bad.put("imageUrl", "http://private-input.example.test/image.jpg");
        request(post("/api/v1/admin/products"), "/api/v1/admin/products", "post", admin, bad, 400);
        request(get("/api/v1/products/invalid-uuid"), "/api/v1/products/{id}", "get", null, null, 400);
    }

    private Product product(long stock, boolean active) {
        var category = merchandise.createCategory("Test supplies", "supplies-" + UUID.randomUUID());
        return merchandise.createProduct(category.id(), "SKU-" + UUID.randomUUID(), "Notebook", "A physical notebook", "https://images.example.com/notebook.jpg", 250000, stock, active);
    }

    private AuthService.Tokens customer() {
        String phone = "09" + String.format("%09d", ThreadLocalRandom.current().nextLong(1_000_000_000L));
        return auth.register("Test Customer", phone, "SyntheticCustomerPassword42!");
    }

    private String admin() { return auth.login("09129999999", adminPassword).accessToken(); }

    private static java.util.HashMap<String, Object> productInput(UUID category, String sku, long price, long stock, boolean active) {
        return new java.util.HashMap<>(Map.of("categoryId", category, "sku", sku, "name", "Notebook", "description", "A physical notebook",
                "imageUrl", "https://images.example.com/notebook.jpg", "price", price, "stock", stock, "active", active));
    }

    private static java.util.HashMap<String, Object> orderInput(UUID request, List<RequestedLine> lines) {
        return new java.util.HashMap<>(Map.of("requestId", request, "address", Map.of("recipientName", "Test Customer", "phoneNumber", "09120000001",
                "province", "Tehran", "city", "Tehran", "addressLine", "Example street, building 12", "postalCode", "1234567890"),
                "items", lines.stream().map(line -> Map.of("productId", line.productId(), "quantity", line.quantity())).toList()));
    }

    private JsonNode request(MockHttpServletRequestBuilder request, String path, String method, String token, Object body, int status) throws Exception {
        if (token != null) request.header("Authorization", "Bearer " + token);
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body));
        var response = mvc.perform(request).andReturn().getResponse();
        assertThat(response.getStatus()).as("%s %s", method, path).isEqualTo(status);
        var result = new OpenApiContract().assertResponse(method, path, response);
        if (status >= 400) assertThat(response.getContentAsString(StandardCharsets.UTF_8)).doesNotContain("private-input", "password_hash", "SELECT ", "INSERT ");
        if (path.contains("orders") && status < 400) assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
        return result;
    }

    private String attempt(UUID user, UUID request, List<RequestedLine> items) {
        try { orders.place(user, request, ADDRESS, items); return "created"; }
        catch (ShopFailure failure) { assertThat(failure.kind()).isEqualTo(ShopFailure.Kind.CONFLICT); return "conflict"; }
    }

    private static <T> List<T> concurrently(List<Callable<T>> work) throws Exception {
        var barrier = new CyclicBarrier(work.size());
        var synchronizedWork = new ArrayList<Callable<T>>();
        for (var task : work) synchronizedWork.add(() -> { barrier.await(10, TimeUnit.SECONDS); return task.call(); });
        try (var executor = Executors.newFixedThreadPool(work.size())) {
            var futures = executor.invokeAll(synchronizedWork, 30, TimeUnit.SECONDS);
            var results = new ArrayList<T>();
            for (var future : futures) results.add(future.get());
            return results;
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Containers {
        @Bean @ServiceConnection
        PostgreSQLContainer postgres() {
            return new PostgreSQLContainer(DockerImageName.parse("docker.io/library/postgres:18.6-alpine").asCompatibleSubstituteFor("postgres"));
        }
    }
}
