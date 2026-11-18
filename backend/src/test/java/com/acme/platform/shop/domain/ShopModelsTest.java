package com.acme.platform.shop.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.acme.platform.shop.domain.ShopModels.Address;
import com.acme.platform.shop.domain.ShopModels.RequestedLine;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ShopModelsTest {
    @Test
    void addressNormalizationAndDiagnosticRedactionPreserveRecipientPrivacy() {
        var address = new Address(" Test Customer ", "09120000001", " Tehran ", "Tehran", " Example street, building 12 ", "1234567890");
        assertThat(address.phoneNumber()).isEqualTo("+989120000001");
        assertThat(address.recipientName()).isEqualTo("Test Customer");
        assertThat(address.toString()).doesNotContain(address.phoneNumber(), address.recipientName(), address.addressLine());
    }

    @Test
    void malformedAddressesAndOrderQuantitiesAreRejected() {
        assertThatThrownBy(() -> new Address("Test Customer", "123", "Tehran", "Tehran", "Example street 12", "1234567890"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Address("Test Customer", "09120000001", "Tehran", "Tehran", "Example street 12", "123"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RequestedLine(UUID.randomUUID(), 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RequestedLine(UUID.randomUUID(), 101)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ShopModels.pagination(10001, 12)).isInstanceOf(IllegalArgumentException.class);
    }
}
