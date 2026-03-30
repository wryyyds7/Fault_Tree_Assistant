package com.cxyaqcdm.fta.validation.dto;

import lombok.Data;
import java.util.List;

@Data
public class ValidationResultDTO {
    private boolean valid;
    private List<ValidationErrorDTO> errors;

    @Data
    public static class ValidationErrorDTO {
        private int code;
        private String nodeId;
        private String message;
        private String errorType;
        private String suggestion;
    }
}
