package com.cxyaqcdm.fta.validation.service;

import com.cxyaqcdm.fta.common.dto.FaultTreeDTO;
import com.cxyaqcdm.fta.validation.dto.ValidationResultDTO;

public interface RuleValidationService {
    ValidationResultDTO validateFaultTree(FaultTreeDTO faultTree);
}
