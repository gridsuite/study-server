/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.networkmodificationtree.dto;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.gridsuite.study.server.networkmodificationtree.entities.NodeType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * @author Jacques Borsenberger <jacques.borsenberger at rte-france.com>
 */
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    property = "type",
    include = JsonTypeInfo.As.EXISTING_PROPERTY,
    visible = true
)
@JsonSubTypes({//Below, we define the names and the binding classes.
    @JsonSubTypes.Type(value = NetworkModificationNode.class, name = "NETWORK_MODIFICATION"),
    @JsonSubTypes.Type(value = RootNode.class, name = "ROOT")
})
@Schema(description = "Basic class for Nodes", subTypes = {NetworkModificationNode.class, RootNode.class}, discriminatorProperty = "type")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
@EqualsAndHashCode
public abstract class AbstractNode {
    UUID id;
    String name;

    @Builder.Default
    List<AbstractNode> children = new ArrayList<>();

    @Builder.Default
    List<UUID> childrenIds = new ArrayList<>();

    String description;

    Integer columnPosition;

    Boolean readOnly;

    NodeType type;
}
