/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service;

import org.gridsuite.study.server.error.StudyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.gridsuite.study.server.error.StudyBusinessErrorCode.NOT_ALLOWED;

/**
 * @author Bassel El Cheikh <bassel.el-cheikh_externe at rte-france.com>
 */

@Service
public class NodeService {
    private final NetworkModificationTreeService networkModificationTreeService;

    public NodeService(NetworkModificationTreeService networkModificationTreeService) {
        this.networkModificationTreeService = networkModificationTreeService;
    }

    @Transactional(readOnly = true)
    public void assertIsNodeNotReadOnly(UUID nodeUuid) {
        Boolean isReadOnly = networkModificationTreeService.isReadOnly(nodeUuid);
        if (Boolean.TRUE.equals(isReadOnly)) {
            throw new StudyException(NOT_ALLOWED);
        }
    }
}
