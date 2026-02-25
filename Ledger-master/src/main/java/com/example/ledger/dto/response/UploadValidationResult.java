package com.example.ledger.dto.response;

/**
 * @author 霜月
 * @create 2025/12/26 20:35
 */

import lombok.Builder;
import lombok.Data;
import lombok.Getter;

import java.util.List;

@Data
@Builder
@Getter
public class UploadValidationResult {
    private Boolean uploadValid;
    private Integer totalRows;
    private Integer validRows;
    private Integer invalidRows;
    private List<RowValidationDetail> rowDetails;
    private List<String> errorMessages;
    private Boolean canProceed; // 是否可以继续处理

    @Data
    @Builder
    public static class RowValidationDetail {
        private Integer rowNumber;
        private Integer excelRowNumber;
        private Boolean isValid;
        private List<FieldValidationDetail> fieldDetails;
        private String rowValidationMessage;

        @Data
        @Builder
        public static class FieldValidationDetail {
            private String fieldName;
            private String fieldLabel;
            private String fieldValue;
            private Boolean isRequired;
            private Boolean isEmpty;
            private String validationMessage;
        }
    }
}