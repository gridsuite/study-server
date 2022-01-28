/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.networkmodificationtree.dto;

import com.powsybl.loadflow.LoadFlowResult;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.gridsuite.study.server.dto.LoadFlowStatus;
import org.gridsuite.study.server.networkmodificationtree.entities.NodeType;

import java.util.UUID;

/**
 * @author Jacques Borsenberger <jacques.borsenberger at rte-france.com>
 */

@AllArgsConstructor
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
public class ModelNode extends AbstractNode {
    String model;

    LoadFlowStatus loadFlowStatus;

    LoadFlowResult loadFlowResult;

    UUID securityAnalysisResultUuid;

    BuildStatus buildStatus;

    @Override
    public NodeType getType() {
        return NodeType.MODEL;
    }
}
