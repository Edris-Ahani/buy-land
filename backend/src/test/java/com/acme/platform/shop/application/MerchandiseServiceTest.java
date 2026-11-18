package com.acme.platform.shop.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.acme.platform.shop.domain.ShopModels.Category;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MerchandiseServiceTest {
    @Test
    void invalidImagesAndPricesCannotBeStored() {
        var store = mock(ShopStore.class);
        var service = new MerchandiseService(store, Clock.systemUTC());
        UUID category = UUID.randomUUID();
        given(store.category(category)).willReturn(Optional.of(new Category(category, "Supplies", "supplies", Instant.EPOCH)));
        for (String image : new String[] {"http://images.example.com/a.jpg", "file:///tmp/a", "https://private:credential@images.example.com/a"}) {
            assertThatThrownBy(() -> service.createProduct(category, "SKU-1", "Notebook", "", image, 1, 1, true))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> service.createProduct(category, "SKU-1", "Notebook", "", "https://images.example.com/a.jpg", 0, 1, true))
                .isInstanceOf(IllegalArgumentException.class);
        verify(store, never()).insertProduct(any());
    }
}
