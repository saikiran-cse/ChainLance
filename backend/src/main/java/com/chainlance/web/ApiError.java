package com.chainlance.web;

import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;

/** The one JSON error format used by every failing API response. Null fields are omitted. */
public record ApiError(Instant timestamp, int status, String error, String code, String message, String path,
                       List<FieldIssue> fieldErrors) {

    public record FieldIssue(String field, String message) {
    }

    public static ApiError of(HttpStatus status, String code, String message, String path, List<FieldIssue> fieldErrors) {
        return new ApiError(Instant.now(), status.value(), status.getReasonPhrase(), code, message, path, fieldErrors);
    }
}
