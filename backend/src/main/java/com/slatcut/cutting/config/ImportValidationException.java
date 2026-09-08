package com.slatcut.cutting.config;

import com.slatcut.cutting.dto.ImportRowError;
import java.util.List;

public class ImportValidationException extends RuntimeException {

    private final List<ImportRowError> errors;

    public ImportValidationException(List<ImportRowError> errors) {
        super("Dữ liệu import không hợp lệ: " + errors.size() + " dòng lỗi");
        this.errors = errors;
    }

    public List<ImportRowError> getErrors() {
        return errors;
    }
}
