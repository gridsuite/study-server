/*
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service;

import org.gridsuite.study.server.networkmodificationtree.entities.*;
import org.gridsuite.study.server.repository.timepoint.TimePointEntity;
import org.gridsuite.study.server.repository.timepoint.TimePointNodeInfoRepository;
import org.gridsuite.study.server.repository.timepoint.TimePointRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * @author Le Saulnier Kevin <lesaulnier.kevin at rte-france.com>
 */
@Service
public class TimePointService {
    private final TimePointNodeInfoRepository timePointNodeInfoRepository;
    private final TimePointRepository timePointRepository;

    public TimePointService(TimePointNodeInfoRepository timePointNodeInfoRepository, TimePointRepository timePointRepository) {
        this.timePointNodeInfoRepository = timePointNodeInfoRepository;
        this.timePointRepository = timePointRepository;
    }

    public UUID getTimePointNetworkUuid(UUID timePointUuid) {
        return timePointRepository.findById(timePointUuid).map(TimePointEntity::getNetworkUuid).orElse(null);
    }

    public TimePointNodeInfoEntity getTimePointNodeInfo(UUID nodeUuid, UUID timePointUuid) {
        return timePointNodeInfoRepository.findByNodeInfoIdAndTimePointId(nodeUuid, timePointUuid);
    }
}
