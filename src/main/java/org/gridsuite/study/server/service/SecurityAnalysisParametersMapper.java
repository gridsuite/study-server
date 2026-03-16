/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service;

import org.gridsuite.study.server.dto.EquipmentsContainer;
import org.gridsuite.study.server.dto.securityanalysis.ContingencyLists;
import org.gridsuite.study.server.dto.securityanalysis.ContingencyListsInfos;
import org.gridsuite.study.server.dto.securityanalysis.SecurityAnalysisParameters;
import org.gridsuite.study.server.dto.securityanalysis.SecurityAnalysisParametersInfos;
import org.gridsuite.study.server.dto.sensianalysis.*;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Caroline Jeandat {@literal <caroline.jeandat at rte-france.com>}
 */
@Service
public class SecurityAnalysisParametersMapper {

    private final DirectoryService directoryService;

    public SecurityAnalysisParametersMapper(DirectoryService directoryService) {
        this.directoryService = directoryService;
    }

    public SecurityAnalysisParameters getParameters(SecurityAnalysisParametersInfos paramsInfos) {

        List<ContingencyLists> contingencyLists = paramsInfos.getContingencyListsInfos().stream()
                .map(clInfos -> new ContingencyLists(
                        EquipmentsContainer.getEquipmentsContainerUuids(clInfos.getContingencyLists()),
                        clInfos.getDescription(),
                        clInfos.isActivated()))
                .toList();

        return SecurityAnalysisParameters.builder()
                .provider(paramsInfos.getProvider())
                .lowVoltageAbsoluteThreshold(paramsInfos.getLowVoltageAbsoluteThreshold())
                .lowVoltageProportionalThreshold(paramsInfos.getLowVoltageProportionalThreshold())
                .highVoltageAbsoluteThreshold(paramsInfos.getHighVoltageAbsoluteThreshold())
                .highVoltageProportionalThreshold(paramsInfos.getHighVoltageProportionalThreshold())
                .flowProportionalThreshold(paramsInfos.getFlowProportionalThreshold())
                .contingencyListsInfos(contingencyLists)
                .limitReductions(paramsInfos.getLimitReductions())
                .build();
    }

    public SecurityAnalysisParametersInfos enrichParametersInfos(SecurityAnalysisParameters params) {
        Map<UUID, String> allContainerNames = getAllContainerNames(params);

        List<ContingencyListsInfos> contingencyListsInfos = params.getContingencyListsInfos().stream()
                .map(cl -> new ContingencyListsInfos(
                        EquipmentsContainer.enrichEquipmentsContainer(cl.getContingencyLists(), allContainerNames),
                        cl.getDescription(),
                        cl.isActivated()))
                .toList();

        return SecurityAnalysisParametersInfos.builder()
                .provider(params.getProvider())
                .lowVoltageAbsoluteThreshold(params.getLowVoltageAbsoluteThreshold())
                .lowVoltageProportionalThreshold(params.getLowVoltageProportionalThreshold())
                .highVoltageAbsoluteThreshold(params.getHighVoltageAbsoluteThreshold())
                .highVoltageProportionalThreshold(params.getHighVoltageProportionalThreshold())
                .flowProportionalThreshold(params.getFlowProportionalThreshold())
                .contingencyListsInfos(contingencyListsInfos)
                .limitReductions(params.getLimitReductions())
                .build();
    }

    private Map<UUID, String> getAllContainerNames(SecurityAnalysisParameters params) {
        Set<UUID> containerIds = params.getContingencyListsInfos().stream()
                .flatMap(cl -> cl.getContingencyLists().stream())
                .collect(Collectors.toSet());

        return directoryService.getElementNames(containerIds, false);
    }
}
