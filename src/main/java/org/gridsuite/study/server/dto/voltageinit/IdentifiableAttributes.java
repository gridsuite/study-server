package org.gridsuite.study.server.dto.voltageinit;

import com.powsybl.iidm.network.IdentifiableType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Identifiable attributes")
public class IdentifiableAttributes {

    @Schema(description = "identifiable id")
    private String id;

    @Schema(description = "identifiable type")
    private IdentifiableType type;

    @Schema(description = "distribution key")
    private Double distributionKey;
}
