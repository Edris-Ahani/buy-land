package com.acme.platform.auth.domain;

import java.util.Locale;

public enum UserType {
    USER,
    ADMIN;

    public String value() { return name().toLowerCase(Locale.ROOT); }

    public static UserType fromValue(String value) {
        for (UserType type : values()) {
            if (type.value().equals(value)) return type;
        }
        throw new IllegalArgumentException("Unknown user type");
    }
}
