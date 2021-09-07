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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
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
    @JsonSubTypes.Type(value = ModelNode.class, name = "MODEL"),
    @JsonSubTypes.Type(value = NetworkModificationNode.class, name = "NETWORK_MODIFICATION"),
    @JsonSubTypes.Type(value = RootNode.class, name = "ROOT")
})
@Schema(description = "Basic class for Filters", subTypes = {ModelNode.class, NetworkModificationNode.class, RootNode.class}, discriminatorProperty = "type")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public abstract class AbstractNode {
    UUID id;
    String name;
    List<AbstractNode> children = new ArrayList<>();
    List<UUID> childrenIds = new ArrayList<>();
    private String description;

    NodeType type;

}
