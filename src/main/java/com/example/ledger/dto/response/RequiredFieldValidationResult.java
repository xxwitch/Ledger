package com.example.ledger.dto.response;

/**
 * @author 霜月
 * @create 2025/12/26 20:34
 */

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class RequiredFieldValidationResult {
    private Boolean isValid;
    private Integer totalFields;
    private Integer requiredFields;
    private Integer emptyRequiredFields;
    private List<FieldValidationDetail> validationDetails;
    private String summaryMessage;

    @Data
    @Builder
    public static class FieldValidationDetail {
        private String fieldName;
        private String fieldLabel;
        private String fieldValue;
        private Boolean isRequired;
        private Boolean isEmpty;
        private String validationMessage;
        private String requiredMessage;
    }
}