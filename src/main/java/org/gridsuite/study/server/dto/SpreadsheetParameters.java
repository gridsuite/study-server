/*
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
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
    private BranchSpreadsheetParameters twt;

    @JsonProperty("GENERATOR")
    @JsonInclude(Include.NON_EMPTY)
    private GeneratorSpreadsheetParameters generator;

    @JsonProperty("BUS")
    @JsonInclude(Include.NON_EMPTY)
    private BusSpreadsheetParameters bus;

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

    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    @Accessors(chain = true)
    @Jacksonized
    @Builder
    public static class BusSpreadsheetParameters {
        @JsonInclude(Include.NON_NULL)
        private Boolean networkComponents;
    }
}
