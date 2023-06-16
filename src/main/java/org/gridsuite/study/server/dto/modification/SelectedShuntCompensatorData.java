package org.gridsuite.study.server.dto.modification;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Builder
@Getter
@EqualsAndHashCode
public class SelectedShuntCompensatorData {
    private String id;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private boolean selected;
}
