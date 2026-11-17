package com.acme.platform.auth.domain;

public final class AuthInput {
    public static final int MAX_NAME_LENGTH = 160;
    public static final int MIN_PASSWORD_LENGTH = 8;
    public static final int MAX_PASSWORD_LENGTH = 128;

    private AuthInput() {}

    public static String fullName(String value) {
        if (value == null || value.length() > MAX_NAME_LENGTH) throw new InvalidField("fullName");
        String normalized = value.replaceAll("(?U)\\s+", " ").strip();
        if (!normalized.matches("[\\p{L}\\p{M}\\p{Zs}\\u200c\\u200d.'-]+")
                || normalized.split(" ").length < 2
                || java.util.Arrays.stream(normalized.split(" ")).anyMatch(part -> !part.matches(".*\\p{L}.*"))) {
            throw new InvalidField("fullName");
        }
        return normalized;
    }

    public static String phoneNumber(String value) {
        if (value == null || value.length() > 32) throw new InvalidField("phoneNumber");
        StringBuilder digits = new StringBuilder();
        for (char character : value.strip().toCharArray()) {
            if (character >= '\u06f0' && character <= '\u06f9') digits.append(character - '\u06f0');
            else if (character >= '\u0660' && character <= '\u0669') digits.append(character - '\u0660');
            else digits.append(character);
        }
        String normalized = digits.toString();
        if (normalized.matches("09[0-9]{9}")) normalized = "+98" + normalized.substring(1);
        if (!normalized.matches("\\+989[0-9]{9}")) throw new InvalidField("phoneNumber");
        return normalized;
    }

    public static String password(String value) {
        if (value == null || value.isBlank() || value.length() < MIN_PASSWORD_LENGTH || value.length() > MAX_PASSWORD_LENGTH) {
            throw new InvalidField("password");
        }
        return value;
    }

    public static final class InvalidField extends IllegalArgumentException {
        private final String field;

        public InvalidField(String field) {
            super("Invalid authentication field");
            this.field = field;
        }

        public String field() { return field; }
    }
}
