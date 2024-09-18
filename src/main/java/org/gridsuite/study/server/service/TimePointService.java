package org.gridsuite.study.server.service;

import org.gridsuite.study.server.networkmodificationtree.entities.*;
import org.gridsuite.study.server.repository.timepoint.TimePointEntity;
import org.gridsuite.study.server.repository.timepoint.TimePointNetworkModificationNodeInfoRepository;
import org.gridsuite.study.server.repository.networkmodificationtree.NetworkModificationNodeInfoRepository;
import org.springframework.stereotype.Service;

@Service
public class TimePointService {
    private final TimePointNetworkModificationNodeInfoRepository timePointNetworkModificationNodeInfoRepository;

    public TimePointService(TimePointNetworkModificationNodeInfoRepository timePointNetworkModificationNodeInfoRepository) {
        this.timePointNetworkModificationNodeInfoRepository = timePointNetworkModificationNodeInfoRepository;
    }

    // create a link between a root node and a timepoint
   /* public TimePointRootNodeInfoEntity createTimePointRootNodeLink(RootNodeInfoEntity rootNodeInfoEntity, TimePointEntity timePointEntity) {
        return timePointRootNodeInfosRepository.save(
            TimePointRootNodeInfoEntity.builder().nodeInfo(rootNodeInfoEntity).timePoint(timePointEntity).build()
        );
    }*/

    // create a link between a network modification node and a timepoint
    /*public TimePointNetworkModificationNodeInfoEntity createTimePointNetworkModificationNodeLink(NetworkModificationNodeInfoEntity modificationNodeInfoEntity, TimePointEntity timePointEntity) {
        return timePointNetworkModificationNodeInfoRepository.save(
            TimePointNetworkModificationNodeInfoEntity.builder().nodeInfo(modificationNodeInfoEntity).timePoint(timePointEntity).build()
        );
    }*/

    // create

    // create a link between a network modification node and a timepoint
    public TimePointNodeInfoEntity createTimePointNodeLink(AbstractNodeInfoEntity modificationNodeInfoEntity, TimePointEntity timePointEntity) {
        return timePointNetworkModificationNodeInfoRepository.save(((NetworkModificationNodeInfoEntity) modificationNodeInfoEntity).toTimePointNodeInfoEntity(timePointEntity));
    }

    /*public TimePointNetworkModificationNodeInfoEntity createTimePointNetworkModificationNodeLink(NetworkModificationNodeInfoEntity modificationNodeInfoEntity, TimePointEntity timePointEntity) {
        return timePointNetworkModificationNodeInfoRepository.save(modificationNodeInfoEntity.toTimePointNodeInfoEntity(timePointEntity));
    }

    public TimePointRootNodeInfoEntity createTimePointNetworkModificationNodeLink(RootNodeInfoEntity modificationNodeInfoEntity, TimePointEntity timePointEntity) {
        return timePointRootNodeInfosRepository.save(modificationNodeInfoEntity.toTimePointNodeInfoEntity(timePointEntity));
    }*/
}
