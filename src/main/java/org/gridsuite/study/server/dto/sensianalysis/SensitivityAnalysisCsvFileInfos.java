package org.gridsuite.study.server.dto.sensianalysis;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SensitivityAnalysisCsvFileInfos {
    private String selector;
    private List<String> csvHeaders;
}
