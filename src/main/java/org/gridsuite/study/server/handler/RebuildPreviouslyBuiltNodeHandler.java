package org.gridsuite.study.server.handler;

import org.gridsuite.study.server.service.NetworkModificationTreeService;
import org.gridsuite.study.server.service.StudyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

@Component
public class RebuildPreviouslyBuiltNodeHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(RebuildPreviouslyBuiltNodeHandler.class);
    private final StudyService studyService;
    private final NetworkModificationTreeService networkModificationTreeService;

    public RebuildPreviouslyBuiltNodeHandler(
        StudyService studyService,
        NetworkModificationTreeService networkModificationTreeService) {
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

        Set<UUID> rootNetworkUuidsWithBuiltNodeBefore =
            studyService.getNodeBuildStatusByRootNetworkUuid(studyUuid, nodeUuid).entrySet().stream()
                .filter(entry -> entry.getValue().isBuilt())
                .map(Map.Entry::getKey).collect(Collectors.toSet());

        T result = operation.call();

        Set<UUID> rootNetworkUuidsWithBuiltNodeAfter =
            studyService.getNodeBuildStatusByRootNetworkUuid(studyUuid, nodeUuid).entrySet().stream()
                .filter(entry -> entry.getValue().isBuilt())
                .map(Map.Entry::getKey).collect(Collectors.toSet());

        try {
            rootNetworkUuidsWithBuiltNodeBefore.stream()
                .filter(uuid -> !rootNetworkUuidsWithBuiltNodeAfter.contains(uuid))
                .forEach(rootNetworkUuid ->
                    studyService.buildNode(
                        studyUuid,
                        nodeUuid,
                        rootNetworkUuid,
                        userId
                    )
                );
        } catch (Exception e) {
            // if rebuild fails, we don't want to rollback main operation transaction
            LOGGER.warn(e.getMessage());
        }

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
