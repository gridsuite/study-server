package org.gridsuite.study.server.service;

import org.gridsuite.study.server.networkmodificationtree.entities.*;
import org.gridsuite.study.server.repository.timepoint.AbstractTimePointNodeInfoRepository;
import org.gridsuite.study.server.repository.timepoint.TimePointEntity;
import org.gridsuite.study.server.repository.timepoint.TimePointNetworkModificationNodeInfoRepository;
import org.gridsuite.study.server.repository.timepoint.TimePointRootNodeInfosRepository;
import org.gridsuite.study.server.repository.networkmodificationtree.NetworkModificationNodeInfoRepository;
import org.springframework.stereotype.Service;

import java.util.EnumMap;

@Service
public class TimePointService {
    private final TimePointRootNodeInfosRepository timePointRootNodeInfosRepository;
    private final TimePointNetworkModificationNodeInfoRepository timePointNetworkModificationNodeInfoRepository;
    private final EnumMap<NodeType, AbstractTimePointNodeInfoRepository<?>> repositories = new EnumMap<>(NodeType.class);
    private final NetworkModificationTreeService networkModificationTreeService;
    private final NetworkModificationNodeInfoRepository networkModificationNodeInfoRepository;

    public TimePointService(TimePointRootNodeInfosRepository timePointRootNodeInfosRepository,
                            NetworkModificationTreeService networkModificationTreeService,
                            NetworkModificationNodeInfoRepository networkModificationNodeInfoRepository,
                            TimePointNetworkModificationNodeInfoRepository timePointNetworkModificationNodeInfoRepository) {
        this.timePointRootNodeInfosRepository = timePointRootNodeInfosRepository;
        this.networkModificationTreeService = networkModificationTreeService;
        this.networkModificationNodeInfoRepository = networkModificationNodeInfoRepository;
        this.timePointNetworkModificationNodeInfoRepository = timePointNetworkModificationNodeInfoRepository;
        repositories.put(NodeType.ROOT, timePointRootNodeInfosRepository);
        repositories.put(NodeType.NETWORK_MODIFICATION, timePointNetworkModificationNodeInfoRepository);

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
    public AbstractTimePointNodeInfoEntity<?> createTimePointNodeLink(AbstractNodeInfoEntity modificationNodeInfoEntity, TimePointEntity timePointEntity) {
        return switch (modificationNodeInfoEntity.getType()) {
            case ROOT ->
                timePointRootNodeInfosRepository.save(((RootNodeInfoEntity) modificationNodeInfoEntity).toTimePointNodeInfoEntity(timePointEntity));
            case NETWORK_MODIFICATION ->
                timePointNetworkModificationNodeInfoRepository.save(((NetworkModificationNodeInfoEntity) modificationNodeInfoEntity).toTimePointNodeInfoEntity(timePointEntity));
        };
    }

    /*public TimePointNetworkModificationNodeInfoEntity createTimePointNetworkModificationNodeLink(NetworkModificationNodeInfoEntity modificationNodeInfoEntity, TimePointEntity timePointEntity) {
        return timePointNetworkModificationNodeInfoRepository.save(modificationNodeInfoEntity.toTimePointNodeInfoEntity(timePointEntity));
    }

    public TimePointRootNodeInfoEntity createTimePointNetworkModificationNodeLink(RootNodeInfoEntity modificationNodeInfoEntity, TimePointEntity timePointEntity) {
        return timePointRootNodeInfosRepository.save(modificationNodeInfoEntity.toTimePointNodeInfoEntity(timePointEntity));
    }*/
}
