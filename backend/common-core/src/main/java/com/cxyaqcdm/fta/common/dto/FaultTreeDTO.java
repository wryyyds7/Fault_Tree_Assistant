package com.cxyaqcdm.fta.common.dto;

import com.cxyaqcdm.fta.common.enums.EventTypeEnum;
import com.cxyaqcdm.fta.common.enums.LogicGateEnum;
import lombok.Data;
import java.util.List;

@Data
public class FaultTreeDTO {
    private String eventId;
    private String eventName;
    private String description;
    private EventTypeEnum eventType;
    private LogicGateEnum gateType;
    private List<FaultTreeDTO> children;
    private String sourceEvidence;
    private String equipmentType;

    private Double confidence;
    private String verificationStatus;
    private Boolean aiGenerated;
    private PositionDTO position;
    private String parentId;
    private Boolean expanded;
    private SourceDetailDTO sourceDetail;

    @Data
    public static class PositionDTO {
        private Double x;
        private Double y;
    }

    @Data
    public static class SourceDetailDTO {
        private String sourceId;
        private String sourceType;
        private String documentName;
        private String pageNumber;
        private String paragraphId;
        private String manualName;
        private String workOrderNumber;
    }
}
