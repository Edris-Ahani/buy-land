package com.acme.platform.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class AuthInputTest {
    @Test
    void normalizesNationalInternationalAndNonLatinDigitsToOneUsername() {
        assertThat(AuthInput.phoneNumber("09120000001")).isEqualTo("+989120000001");
        assertThat(AuthInput.phoneNumber(" +989120000001 ")).isEqualTo("+989120000001");
        assertThat(AuthInput.phoneNumber("\u06f0\u06f9\u06f1\u06f2\u06f0\u06f0\u06f0\u06f0\u06f0\u06f0\u06f1")).isEqualTo("+989120000001");
        assertThat(AuthInput.phoneNumber("\u0660\u0669\u0661\u0662\u0660\u0660\u0660\u0660\u0660\u0660\u0661")).isEqualTo("+989120000001");
    }

    @Test
    void requiresAnIranianMobileNumber() {
        for (String phone : new String[]{"", "02120000001", "0912000000", "091200000011", "+441234567890", "0912-000-0001", "0912abc0001"}) {
            assertThatThrownBy(() -> AuthInput.phoneNumber(phone)).isInstanceOf(AuthInput.InvalidField.class);
        }
    }

    @Test
    void preservesUnicodeNamesAndRequiresGivenAndFamilyNames() {
        String name = "\u0627\u062f\u0631\u06cc\u0633 \u0622\u0647\u0646\u06cc";
        assertThat(AuthInput.fullName(" " + name + " ")).isEqualTo(name);
        assertThat(AuthInput.fullName("Edris  Ahani")).isEqualTo("Edris Ahani");
        for (String invalid : new String[]{"Edris", " ", "Edris 123", "<script> Test", "Edris -", "a".repeat(161)}) {
            assertThatThrownBy(() -> AuthInput.fullName(invalid)).isInstanceOf(AuthInput.InvalidField.class);
        }
    }

    @Test
    void neverTrimsPasswordsAndEnforcesBounds() {
        assertThat(AuthInput.password(" password ")).isEqualTo(" password ");
        assertThatThrownBy(() -> AuthInput.password("short")).isInstanceOf(AuthInput.InvalidField.class);
        assertThatThrownBy(() -> AuthInput.password("a".repeat(129))).isInstanceOf(AuthInput.InvalidField.class);
        assertThatThrownBy(() -> AuthInput.password("        ")).isInstanceOf(AuthInput.InvalidField.class);
    }
}
