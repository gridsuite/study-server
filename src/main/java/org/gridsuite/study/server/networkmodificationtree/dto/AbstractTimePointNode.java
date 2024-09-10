package org.gridsuite.study.server.networkmodificationtree.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

@SuperBuilder
@Getter
@NoArgsConstructor
@Setter
public abstract class AbstractTimePointNode {
    private NetworkModificationNode node;

    private TimePoint timePoint;

    @JsonIgnore
    private UUID reportUuid;
}
