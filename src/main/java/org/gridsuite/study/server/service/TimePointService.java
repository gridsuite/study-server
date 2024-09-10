package org.gridsuite.study.server.service;

import org.gridsuite.study.server.networkmodificationtree.dto.RootNode;
import org.gridsuite.study.server.networkmodificationtree.entities.*;
import org.gridsuite.study.server.repository.StudyEntity;
import org.gridsuite.study.server.repository.TimePointEntity;
import org.gridsuite.study.server.repository.TimePointRootNodeInfosRepository;
import org.gridsuite.study.server.repository.networkmodificationtree.NetworkModificationNodeInfoRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TimePointService {
    private final TimePointRootNodeInfosRepository timePointRootNodeInfosRepository;
    private final NetworkModificationTreeService networkModificationTreeService;

    public TimePointService(TimePointRootNodeInfosRepository timePointRootNodeInfosRepository,
                            NetworkModificationTreeService networkModificationTreeService) {
        this.timePointRootNodeInfosRepository = timePointRootNodeInfosRepository;
        this.networkModificationTreeService = networkModificationTreeService;
    }

    public void linkAllStudyNodesToTimePoint(StudyEntity studyEntity, TimePointEntity timePointEntity) {
        TimePointEntity timePoint = studyEntity.getFirstTimepoint();
        List<NodeEntity> nodeEntities = networkModificationTreeService.getAllNodes(studyEntity.getId());
        rootNode
    }

    // create a link between a root node and a timepoint
    public TimePointRootNodeInfoEntity createTimePointRootNodeLink(RootNodeInfoEntity node, TimePointEntity timePointEntity) {
        TimePointRootNodeInfoEntity timePointNodeStatusEntity = TimePointRootNodeInfoEntity.builder()
            .nodeInfo(node)
            .timePoint(timePointEntity)
            .build();

        return timePointRootNodeInfosRepository.save(timePointNodeStatusEntity);
    }
}
