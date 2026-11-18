package com.acme.platform.shop.web;

import com.acme.platform.platform.web.ApiProblems;
import com.acme.platform.shop.domain.ShopFailure;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = {PublicShopController.class, AdminShopController.class, CustomerOrdersController.class, AdminOrdersController.class})
public class ShopExceptionHandler {
    @ExceptionHandler(ShopFailure.class)
    ProblemDetail businessFailure(ShopFailure exception) {
        return ApiProblems.create(exception.kind() == ShopFailure.Kind.NOT_FOUND ? HttpStatus.NOT_FOUND : HttpStatus.CONFLICT, exception.getMessage());
    }
}
