package com.acme.platform.auth.web;

import com.acme.platform.auth.application.AuthService.InvalidCredentials;
import com.acme.platform.auth.application.AuthService.PhoneAlreadyRegistered;
import com.acme.platform.auth.domain.AuthInput.InvalidField;
import com.acme.platform.platform.web.ApiProblems;
import java.util.List;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = AuthController.class)
public class AuthExceptionHandler {
    @ExceptionHandler(InvalidField.class)
    ProblemDetail invalidField(InvalidField exception) {
        ProblemDetail problem = ApiProblems.create(HttpStatus.BAD_REQUEST, "One or more fields are invalid");
        problem.setProperty("invalidFields", List.of(exception.field()));
        return problem;
    }

    @ExceptionHandler(InvalidCredentials.class)
    ResponseEntity<ProblemDetail> invalidCredentials() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).header(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
                .body(ApiProblems.create(HttpStatus.UNAUTHORIZED, "The credentials or token are invalid or expired"));
    }

    @ExceptionHandler(PhoneAlreadyRegistered.class)
    ProblemDetail duplicatePhone() {
        return ApiProblems.create(HttpStatus.CONFLICT, "An account with this phone number already exists");
    }
}
