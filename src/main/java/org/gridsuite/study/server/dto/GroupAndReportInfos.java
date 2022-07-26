package org.gridsuite.study.server.dto;

import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * @author Laurent Garnier <laurent.garnier@rte-france.com>
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Schema(description = "report usage infos")
public class GroupAndReportInfos {
    private UUID groupUuid;
    private UUID reportUuid;
}
