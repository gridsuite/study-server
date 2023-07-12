package org.gridsuite.study.server.dto.supervision;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.gridsuite.study.server.dto.StudyInfos;

import java.util.UUID;

@SuperBuilder
@NoArgsConstructor
@Getter
@ToString(callSuper = true)
@Schema(description = "Supervision Study attributes")
public class SupervisionStudyInfos extends StudyInfos {
    private UUID networkUuid;
}
