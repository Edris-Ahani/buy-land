package com.acme.platform.shop.persistence;

import com.acme.platform.shop.application.ShopStore;
import com.acme.platform.shop.domain.ShopFailure;
import com.acme.platform.shop.domain.ShopModels.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcShopStore implements ShopStore {
    private final JdbcClient jdbc;
    public JdbcShopStore(JdbcClient jdbc) { this.jdbc = jdbc; }

    @Override
    public Page<Category> categories(int page, int size) {
        long total = jdbc.sql("SELECT count(*) FROM shop_categories").query(Long.class).single();
        var rows = jdbc.sql("SELECT * FROM shop_categories ORDER BY created_at DESC, id DESC LIMIT :size OFFSET :offset")
                .param("size", size).param("offset", page * size).query((row, index) -> category(row)).list();
        return page(rows, page, size, total);
    }

    @Override public Optional<Category> category(UUID id) {
        return jdbc.sql("SELECT * FROM shop_categories WHERE id = :id").param("id", id).query((row, index) -> category(row)).optional();
    }

    @Override public void insertCategory(Category category) {
        int inserted = jdbc.sql("INSERT INTO shop_categories (id, name, slug, created_at) VALUES (:id, :name, :slug, :created) ON CONFLICT (slug) DO NOTHING")
                .param("id", category.id()).param("name", category.name()).param("slug", category.slug())
                .param("created", Timestamp.from(category.createdAt())).update();
        if (inserted == 0) throw ShopFailure.conflict("Category slug already exists");
    }

    @Override public Optional<Category> updateCategory(UUID id, String name, String slug) {
        try {
            return jdbc.sql("UPDATE shop_categories SET name = :name, slug = :slug WHERE id = :id RETURNING *")
                    .param("id", id).param("name", name).param("slug", slug).query((row, index) -> category(row)).optional();
        } catch (DuplicateKeyException exception) {
            throw ShopFailure.conflict("Category slug already exists");
        }
    }

    @Override public Page<Product> products(UUID categoryId, String query, boolean includeInactive, int page, int size) {
        String filter = " WHERE (:includeInactive OR active = true) AND (CAST(:category AS UUID) IS NULL OR category_id = :category)"
                + " AND (strpos(lower(name), lower(:query)) > 0 OR strpos(lower(sku), lower(:query)) > 0)";
        long total = productFilter(jdbc.sql("SELECT count(*) FROM shop_products" + filter), categoryId, query, includeInactive).query(Long.class).single();
        var rows = productFilter(jdbc.sql("SELECT * FROM shop_products" + filter + " ORDER BY created_at DESC, id DESC LIMIT :size OFFSET :offset"),
                categoryId, query, includeInactive).param("size", size).param("offset", page * size)
                .query((row, index) -> product(row)).list();
        return page(rows, page, size, total);
    }

    private JdbcClient.StatementSpec productFilter(JdbcClient.StatementSpec statement, UUID categoryId, String query, boolean includeInactive) {
        return statement.param("category", categoryId, java.sql.Types.OTHER).param("query", query).param("includeInactive", includeInactive);
    }

    @Override public Optional<Product> product(UUID id) {
        return jdbc.sql("SELECT * FROM shop_products WHERE id = :id").param("id", id).query((row, index) -> product(row)).optional();
    }

    @Override public List<Product> lockProducts(List<UUID> ids) {
        if (ids.isEmpty()) return List.of();
        return jdbc.sql("SELECT * FROM shop_products WHERE id IN (:ids) ORDER BY id FOR UPDATE")
                .param("ids", ids).query((row, index) -> product(row)).list();
    }

    @Override public void insertProduct(Product product) {
        int inserted = productParameters(jdbc.sql("""
                INSERT INTO shop_products (id, category_id, sku, name, description, image_url, price, stock, active, version, created_at, updated_at)
                VALUES (:id, :category, :sku, :name, :description, :image, :price, :stock, :active, :version, :created, :updated)
                ON CONFLICT (sku) DO NOTHING
                """), product).update();
        if (inserted == 0) throw ShopFailure.conflict("Product SKU already exists");
    }

    @Override public boolean updateProduct(Product product, long expectedVersion) {
        try {
            return productParameters(jdbc.sql("""
                UPDATE shop_products SET category_id = :category, sku = :sku, name = :name, description = :description,
                    image_url = :image, price = :price, stock = :stock, active = :active, version = :version, updated_at = :updated
                WHERE id = :id AND version = :expected
                    """), product).param("expected", expectedVersion).update() == 1;
        } catch (DuplicateKeyException exception) {
            throw ShopFailure.conflict("Product SKU already exists");
        }
    }

    private JdbcClient.StatementSpec productParameters(JdbcClient.StatementSpec statement, Product product) {
        return statement.param("id", product.id()).param("category", product.categoryId()).param("sku", product.sku())
                .param("name", product.name()).param("description", product.description()).param("image", product.imageUrl())
                .param("price", product.price()).param("stock", product.stock()).param("active", product.active()).param("version", product.version())
                .param("created", Timestamp.from(product.createdAt())).param("updated", Timestamp.from(product.updatedAt()));
    }

    @Override public void changeStock(UUID id, long difference, Instant now) {
        int updated = jdbc.sql("UPDATE shop_products SET stock = stock + :difference, version = version + 1, updated_at = :now WHERE id = :id AND stock + :difference >= 0")
                .param("id", id).param("difference", difference).param("now", Timestamp.from(now)).update();
        if (updated != 1) throw ShopFailure.conflict("Insufficient product stock");
    }

    @Override public boolean insertOrder(OrderHeader header) {
        return jdbc.sql("""
                INSERT INTO shop_orders (id, user_id, request_id, status, total, recipient_name, phone_number, province, city,
                    address_line, postal_code, created_at, updated_at)
                VALUES (:id, :user, :request, :status, :total, :recipient, :phone, :province, :city, :address, :postal, :created, :updated)
                ON CONFLICT (user_id, request_id) DO NOTHING
                """)
                .param("id", header.id()).param("user", header.userId()).param("request", header.requestId())
                .param("status", header.status().value()).param("total", header.total()).param("recipient", header.address().recipientName())
                .param("phone", header.address().phoneNumber()).param("province", header.address().province()).param("city", header.address().city())
                .param("address", header.address().addressLine()).param("postal", header.address().postalCode())
                .param("created", Timestamp.from(header.createdAt())).param("updated", Timestamp.from(header.updatedAt())).update() == 1;
    }

    @Override public Optional<OrderHeader> orderByRequest(UUID userId, UUID requestId) {
        return jdbc.sql("SELECT * FROM shop_orders WHERE user_id = :user AND request_id = :request")
                .param("user", userId).param("request", requestId).query((row, index) -> order(row)).optional();
    }

    @Override public Optional<OrderHeader> order(UUID id, boolean lock) {
        return jdbc.sql("SELECT * FROM shop_orders WHERE id = :id" + (lock ? " FOR UPDATE" : ""))
                .param("id", id).query((row, index) -> order(row)).optional();
    }

    @Override public List<OrderLine> orderLines(UUID orderId) {
        return jdbc.sql("SELECT * FROM shop_order_items WHERE order_id = :id ORDER BY position")
                .param("id", orderId).query((row, index) -> new OrderLine(row.getObject("product_id", UUID.class), row.getString("sku"),
                        row.getString("name"), row.getLong("unit_price"), row.getInt("quantity"), row.getLong("line_total"))).list();
    }

    @Override public void insertOrderLines(UUID orderId, List<OrderLine> lines) {
        // Orders have at most 50 lines; all inserts participate in the application transaction.
        for (int index = 0; index < lines.size(); index++) {
            var line = lines.get(index);
            jdbc.sql("INSERT INTO shop_order_items (order_id, product_id, position, sku, name, unit_price, quantity, line_total) VALUES (:order, :product, :position, :sku, :name, :price, :quantity, :total)")
                    .param("order", orderId).param("product", line.productId()).param("position", index).param("sku", line.sku())
                    .param("name", line.name()).param("price", line.unitPrice()).param("quantity", line.quantity()).param("total", line.lineTotal()).update();
        }
    }

    @Override public void setOrderTotal(UUID id, long total) {
        jdbc.sql("UPDATE shop_orders SET total = :total WHERE id = :id").param("id", id).param("total", total).update();
    }

    @Override public void setOrderStatus(UUID id, OrderStatus status, String carrier, String trackingCode, Instant now) {
        jdbc.sql("UPDATE shop_orders SET status = :status, carrier = :carrier, tracking_code = :tracking, updated_at = :now WHERE id = :id")
                .param("id", id).param("status", status.value()).param("carrier", carrier, java.sql.Types.VARCHAR)
                .param("tracking", trackingCode, java.sql.Types.VARCHAR).param("now", Timestamp.from(now)).update();
    }

    @Override public Page<OrderHeader> orders(UUID userId, OrderStatus status, int page, int size) {
        String filter = " WHERE (CAST(:user AS UUID) IS NULL OR user_id = :user) AND (CAST(:status AS VARCHAR) IS NULL OR status = :status)";
        long total = orderFilter(jdbc.sql("SELECT count(*) FROM shop_orders" + filter), userId, status).query(Long.class).single();
        var rows = orderFilter(jdbc.sql("SELECT * FROM shop_orders" + filter + " ORDER BY created_at DESC, id DESC LIMIT :size OFFSET :offset"), userId, status)
                .param("size", size).param("offset", page * size).query((row, index) -> order(row)).list();
        return page(rows, page, size, total);
    }

    private JdbcClient.StatementSpec orderFilter(JdbcClient.StatementSpec statement, UUID userId, OrderStatus status) {
        return statement.param("user", userId, java.sql.Types.OTHER).param("status", status == null ? null : status.value(), java.sql.Types.VARCHAR);
    }

    private static <T> Page<T> page(List<T> rows, int page, int size, long total) {
        return new Page<>(rows, page, size, total, (int) ((total + size - 1) / size));
    }

    private static Category category(ResultSet row) throws SQLException {
        return new Category(row.getObject("id", UUID.class), row.getString("name"), row.getString("slug"), row.getTimestamp("created_at").toInstant());
    }

    private static Product product(ResultSet row) throws SQLException {
        return new Product(row.getObject("id", UUID.class), row.getObject("category_id", UUID.class), row.getString("sku"),
                row.getString("name"), row.getString("description"), row.getString("image_url"), row.getLong("price"), row.getLong("stock"),
                row.getBoolean("active"), row.getLong("version"), row.getTimestamp("created_at").toInstant(), row.getTimestamp("updated_at").toInstant());
    }

    private static OrderHeader order(ResultSet row) throws SQLException {
        return new OrderHeader(row.getObject("id", UUID.class), row.getObject("user_id", UUID.class), row.getObject("request_id", UUID.class),
                OrderStatus.fromValue(row.getString("status")), row.getLong("total"), new Address(row.getString("recipient_name"), row.getString("phone_number"),
                        row.getString("province"), row.getString("city"), row.getString("address_line"), row.getString("postal_code")),
                row.getString("carrier"), row.getString("tracking_code"), row.getTimestamp("created_at").toInstant(), row.getTimestamp("updated_at").toInstant());
    }
}
