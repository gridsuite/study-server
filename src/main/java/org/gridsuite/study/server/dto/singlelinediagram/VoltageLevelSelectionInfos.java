package org.gridsuite.study.server.dto.singlelinediagram;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * @author Maissa SOUISSI <maissa.souissi at rte-france.com>
 */

@Getter
@Builder
public class VoltageLevelSelectionInfos {
    private List<String> voltageLevelsIds;
    private List<String> expandedVoltageLevelIds;
}
