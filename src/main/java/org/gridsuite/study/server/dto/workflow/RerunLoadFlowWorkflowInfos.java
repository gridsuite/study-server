/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.dto.workflow;

import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

/**
 * @author Kevin Le Saulnier <kevin.lesaulnier at rte-france.com>
 */
@Getter
@Builder
public class RerunLoadFlowWorkflowInfos extends AbstractWorkflowInfos {
    UUID loadflowResultUuid;
    String userId;
    boolean withRatioTapChangers;

    @Override
    public WorkflowType getWorkflowType() {
        return WorkflowType.RERUN_LOAD_FLOW;
    }
}
