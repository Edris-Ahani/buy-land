package com.acme.platform.shop.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.acme.platform.shop.domain.ShopModels.*;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OrderServiceTest {
    private final ShopStore store = mock(ShopStore.class);
    private final OrderService service = new OrderService(store, Clock.systemUTC());

    @Test
    void duplicateOrEmptyOrderLinesCannotStartAnOrderTransaction() {
        UUID product = UUID.randomUUID();
        var address = new Address("Test Customer", "09120000001", "Tehran", "Tehran", "Example street, building 12", "1234567890");
        assertThatThrownBy(() -> service.place(UUID.randomUUID(), UUID.randomUUID(), address, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.place(UUID.randomUUID(), UUID.randomUUID(), address,
                List.of(new RequestedLine(product, 1), new RequestedLine(product, 2))))
                .isInstanceOf(IllegalArgumentException.class);
        verify(store, never()).insertOrder(any());
    }
}
