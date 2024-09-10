package org.gridsuite.study.server.networkmodificationtree.dto;

import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

@SuperBuilder
@NoArgsConstructor
@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
public class TimePointRootNode extends AbstractTimePointNode {
    UUID studyId;
}
