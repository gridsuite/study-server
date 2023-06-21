package org.gridsuite.study.server.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;

/**
 * @author David Braquart <david.braquart at rte-france.com>
 */
@Builder
@Getter
@EqualsAndHashCode
public class HvdcDeletionSelectedShuntCompensatorData {
    private String id;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private boolean selected;
}
