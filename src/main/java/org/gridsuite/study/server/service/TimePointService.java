package org.gridsuite.study.server.service;

import org.gridsuite.study.server.networkmodificationtree.entities.*;
import org.gridsuite.study.server.repository.timepoint.TimePointEntity;
import org.gridsuite.study.server.repository.timepoint.TimePointNodeInfoRepository;
import org.gridsuite.study.server.repository.timepoint.TimePointRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

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
