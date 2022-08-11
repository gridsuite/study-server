package org.gridsuite.study.server.dto;

import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class CaseImportReceiver {
    private UUID studyUuid;
    private UUID caseUuid;
    private UUID reportUuid;
    private String userId;
    private Long startTime;
}
