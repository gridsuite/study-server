package org.gridsuite.study.server.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.*;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudyImportInfos {
    private String exportVersion;
    private UUID studyUuid;
    private String studyName;
    private List<RootNetworkExportInfos> rootNetworks;
    private JsonNode rootNode;
}
