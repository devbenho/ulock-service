package com.codgo.ulock.common.error;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/**
 * Credentials or tokens were rejected. The detail is deliberately generic so responses never reveal
 * whether a tenant or account exists, or why exactly the attempt failed.
 */
public class AuthenticationFailedException extends ErrorResponseException {

    public AuthenticationFailedException(String detail) {
        super(HttpStatus.UNAUTHORIZED, ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, detail), null);
    }
}
