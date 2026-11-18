package com.acme.platform.shop.domain;

public final class ShopFailure extends RuntimeException {
    public enum Kind { NOT_FOUND, CONFLICT }
    private final Kind kind;
    public ShopFailure(Kind kind, String detail) { super(detail); this.kind = kind; }
    public Kind kind() { return kind; }
    public static ShopFailure notFound() { return new ShopFailure(Kind.NOT_FOUND, "The requested resource was not found"); }
    public static ShopFailure conflict(String detail) { return new ShopFailure(Kind.CONFLICT, detail); }
}
