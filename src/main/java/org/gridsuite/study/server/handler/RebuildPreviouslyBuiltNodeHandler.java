package org.gridsuite.study.server.handler;

import org.gridsuite.study.server.service.NetworkModificationTreeService;
import org.gridsuite.study.server.service.StudyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.function.Predicate;
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
        UUID node1Uuid,
        UUID node2Uuid,
        String userId,
        Callable<T> operation
    ) throws Exception {
        // if node 1 and 2 are in the same "subtree", rebuild only the highest one - otherwise, rebuild both
        List<UUID> nodesToReBuild = networkModificationTreeService.getHighestNodeUuids(node1Uuid, node2Uuid).stream()
            .filter(Predicate.not(networkModificationTreeService::isRootOrConstructionNode)).toList();

        if (nodesToReBuild.isEmpty()) {
            return operation.call();
        }

        Map<UUID, Set<UUID>> rootNetworkUuidsWithBuiltNodeBeforeMap = nodesToReBuild.stream().collect(Collectors.toMap(
            nodeUuid -> nodeUuid,
            nodeUuid -> getRootNetworkWhereNotHasToBeRebuilt(studyUuid, nodeUuid)
        ));

        T result = operation.call();

        Map<UUID, Set<UUID>> rootNetworkUuidsWithBuiltNodeAfterMap = nodesToReBuild.stream().collect(Collectors.toMap(
            nodeUuid -> nodeUuid,
            nodeUuid -> getRootNetworkWhereNotHasToBeRebuilt(studyUuid, nodeUuid)
        ));

        try {
            rootNetworkUuidsWithBuiltNodeAfterMap.forEach((nodeUuid, rootNetworkUuidsWithBuiltNodeAfter) -> {
                Set<UUID> rootNetworkUuidsWithBuiltNodeBefore = rootNetworkUuidsWithBuiltNodeBeforeMap.get(nodeUuid);

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
            });
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

    public void execute(
        UUID studyUuid,
        UUID node1Uuid,
        UUID node2Uuid,
        String userId,
        Runnable operation
    ) {
        try {
            execute(
                studyUuid,
                node1Uuid,
                node2Uuid,
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

    private Set<UUID> getRootNetworkWhereNotHasToBeRebuilt(UUID studyUuid, UUID nodeUuid) {
        return studyService.getNodeBuildStatusByRootNetworkUuid(studyUuid, nodeUuid).entrySet().stream()
            .filter(entry -> entry.getValue().isBuilt())
            .map(Map.Entry::getKey).collect(Collectors.toSet());
    }
}
