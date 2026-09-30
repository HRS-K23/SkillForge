package com.project.skillforge.shared.error;

import java.util.List;

public record ApiError(String code, String message, List<FieldViolation> details) {
    public record FieldViolation(String field, String message) {}
}
