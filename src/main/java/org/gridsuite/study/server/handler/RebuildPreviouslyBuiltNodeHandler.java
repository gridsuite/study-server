package org.gridsuite.study.server.handler;

import org.gridsuite.study.server.networkmodificationtree.dto.NodeBuildStatus;
import org.gridsuite.study.server.service.NetworkModificationTreeService;
import org.gridsuite.study.server.service.StudyService;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;

@Component
public class RebuildPreviouslyBuiltNodeHandler {
    private final StudyService studyService;
    private final NetworkModificationTreeService networkModificationTreeService;

    public RebuildPreviouslyBuiltNodeHandler(
        StudyService studyService,
        NetworkModificationTreeService networkModificationTreeService
    ) {
        this.studyService = studyService;
        this.networkModificationTreeService = networkModificationTreeService;
    }

    public <T> T execute(
        UUID studyUuid,
        UUID nodeUuid,
        String userId,
        Callable<T> operation
    ) throws Exception {
        if (networkModificationTreeService.isRootOrConstructionNode(nodeUuid)) {
            return operation.call();
        }

        Map<UUID, NodeBuildStatus> previousBuildStatus =
            studyService.getNodeBuildStatusByRootNetworkUuid(studyUuid, nodeUuid);

        T result = operation.call();

        previousBuildStatus.entrySet().stream()
            .filter(entry -> entry.getValue().isBuilt())
            .forEach(entry ->
                studyService.buildNode(
                    studyUuid,
                    nodeUuid,
                    entry.getKey(),
                    userId
            ));

        return result;
    }

    public void execute(
        UUID studyUuid,
        UUID nodeUuid,
        String userId,
        Runnable operation
    ) {
        try {
            execute(
                studyUuid,
                nodeUuid,
                userId,
                () -> {
                    operation.run();
                    return null;
                }
            );
        } catch (Exception e) {
            throw new RuntimeException(e); //TODO: better exception
        }
    }
}
