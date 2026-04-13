package com.cxyaqcdm.fta.validation.rules;

import com.cxyaqcdm.fta.validation.dto.ValidationResultDTO;
import java.util.List;

public class AddError {

    public static void addError(String code, String nodeId, String message,
                                String errorType, String suggestion,
                                List<ValidationResultDTO.ValidationErrorDTO> errors) {
        ValidationResultDTO.ValidationErrorDTO error = new ValidationResultDTO.ValidationErrorDTO();
        error.setCode(code);
        error.setNodeId(nodeId);
        error.setMessage(message);
        error.setErrorType(errorType);
        error.setSuggestion(suggestion);
        errors.add(error);
    }
}