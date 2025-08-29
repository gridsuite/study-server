package org.gridsuite.study.server.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;
import lombok.extern.jackson.Jacksonized;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Accessors(chain = true)
@Jacksonized
@Builder
public class SpreadsheetParameters {
    @JsonProperty("BRANCH")
    @JsonInclude(Include.NON_EMPTY)
    private BranchSpreadsheetParameters branch;

    @JsonProperty("LINE")
    @JsonInclude(Include.NON_EMPTY)
    private BranchSpreadsheetParameters line;

    @JsonProperty("TWO_WINDINGS_TRANSFORMER")
    @JsonInclude(Include.NON_EMPTY)
    private BranchSpreadsheetParameters t2w;

    @JsonProperty("GENERATOR")
    @JsonInclude(Include.NON_EMPTY)
    private GeneratorSpreadsheetParameters generator;

    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    @Accessors(chain = true)
    @Jacksonized
    @SuperBuilder
    public static class BranchSpreadsheetParameters {
        @JsonInclude(Include.NON_NULL)
        private Boolean operationalLimitsGroups;
    }

    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    @Accessors(chain = true)
    @Jacksonized
    @Builder
    public static class GeneratorSpreadsheetParameters {
        @JsonInclude(Include.NON_NULL)
        private Boolean regulatingTerminal;
    }
}
