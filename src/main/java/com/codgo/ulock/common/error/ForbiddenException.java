package com.codgo.ulock.common.error;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

public class ForbiddenException extends ErrorResponseException {

    public ForbiddenException(String detail) {
        super(HttpStatus.FORBIDDEN, ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, detail), null);
    }
}
