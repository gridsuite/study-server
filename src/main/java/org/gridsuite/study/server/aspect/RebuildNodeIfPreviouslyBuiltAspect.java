/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.aspect;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.gridsuite.study.server.annotation.RebuildNodeUuid;
import org.gridsuite.study.server.annotation.RebuildStudyUuid;
import org.gridsuite.study.server.annotation.RebuildUserId;
import org.gridsuite.study.server.networkmodificationtree.dto.NodeBuildStatus;
import org.gridsuite.study.server.service.NetworkModificationTreeService;
import org.gridsuite.study.server.service.StudyService;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;

/**
 * @author Kevin Le Saulnier <kevin.le-saulnier at rte-france.com>
 */
@Aspect
@Component
public class RebuildNodeIfPreviouslyBuiltAspect {

    private final StudyService studyService;
    private final NetworkModificationTreeService networkModificationTreeService;

    public RebuildNodeIfPreviouslyBuiltAspect(StudyService studyService, NetworkModificationTreeService networkModificationTreeService) {
        this.studyService = studyService;
        this.networkModificationTreeService = networkModificationTreeService;
    }

    @Around("@annotation(org.gridsuite.study.server.annotation.RebuildNodeIfPreviouslyBuilt)")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        Method method = signature.getMethod();
        Object[] args = pjp.getArgs();

        UUID nodeUuid = AnnotatedParameterExtractor.extractRequiredParameter(method, args, RebuildNodeUuid.class, UUID.class);

        if (networkModificationTreeService.isRootOrConstructionNode(nodeUuid)) {
            // This aspect only rebuilds security nodes
            return pjp.proceed();
        }

        UUID studyUuid = AnnotatedParameterExtractor.extractRequiredParameter(method, args, RebuildStudyUuid.class, UUID.class);
        String userId = AnnotatedParameterExtractor.extractRequiredParameter(method, args, RebuildUserId.class, String.class);

        Map<UUID, NodeBuildStatus> buildStatusByRootNetworkUuid = studyService.getNodeBuildStatusByRootNetworkUuid(studyUuid, nodeUuid);

        Object result = pjp.proceed();

        // No try catch -> do not rebuild node if operation has failed
        buildStatusByRootNetworkUuid.entrySet().stream()
            .filter(entry -> entry.getValue().isBuilt())
            .forEach(entry -> studyService.buildNode(studyUuid, nodeUuid, entry.getKey(), userId));
        return result;
    }
}
