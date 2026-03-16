/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service;

import org.gridsuite.study.server.dto.EquipmentsContainer;
import org.gridsuite.study.server.dto.sensianalysis.*;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * @author Caroline Jeandat {@literal <caroline.jeandat at rte-france.com>}
 */
@Service
public class SensitivityAnalysisParametersMapper {

    private final DirectoryService directoryService;

    public SensitivityAnalysisParametersMapper(DirectoryService directoryService) {
        this.directoryService = directoryService;
    }

    public SensitivityAnalysisParameters getParameters(SensitivityAnalysisParametersInfos paramsInfos) {

        List<SensitivityInjectionsSet> sensiInjectionsSets = paramsInfos.getSensitivityInjectionsSet().stream()
                .map(injectionsSetInfos -> new SensitivityInjectionsSet(
                        EquipmentsContainer.getEquipmentsContainerUuids(injectionsSetInfos.getMonitoredBranches()),
                        EquipmentsContainer.getEquipmentsContainerUuids(injectionsSetInfos.getInjections()),
                        injectionsSetInfos.getDistributionType(),
                        EquipmentsContainer.getEquipmentsContainerUuids(injectionsSetInfos.getContingencies()),
                        injectionsSetInfos.isActivated()))
                .toList();

        List<SensitivityInjection> sensiInjections = paramsInfos.getSensitivityInjection().stream()
                .map(injectionInfos -> new SensitivityInjection(
                        EquipmentsContainer.getEquipmentsContainerUuids(injectionInfos.getMonitoredBranches()),
                        EquipmentsContainer.getEquipmentsContainerUuids(injectionInfos.getInjections()),
                        EquipmentsContainer.getEquipmentsContainerUuids(injectionInfos.getContingencies()),
                        injectionInfos.isActivated()))
                .toList();

        List<SensitivityHVDC> sensiHvdcs = paramsInfos.getSensitivityHVDC().stream()
                .map(hvdcInfos -> new SensitivityHVDC(
                        EquipmentsContainer.getEquipmentsContainerUuids(hvdcInfos.getMonitoredBranches()),
                        hvdcInfos.getSensitivityType(),
                        EquipmentsContainer.getEquipmentsContainerUuids(hvdcInfos.getHvdcs()),
                        EquipmentsContainer.getEquipmentsContainerUuids(hvdcInfos.getContingencies()),
                        hvdcInfos.isActivated()))
                .toList();

        List<SensitivityPST> sensiPsts = paramsInfos.getSensitivityPST().stream()
                .map(pstInfos -> new SensitivityPST(
                        EquipmentsContainer.getEquipmentsContainerUuids(pstInfos.getMonitoredBranches()),
                        pstInfos.getSensitivityType(),
                        EquipmentsContainer.getEquipmentsContainerUuids(pstInfos.getPsts()),
                        EquipmentsContainer.getEquipmentsContainerUuids(pstInfos.getContingencies()),
                        pstInfos.isActivated()))
                .toList();

        List<SensitivityNodes> sensiNodes = paramsInfos.getSensitivityNodes().stream()
                .map(nodeInfos -> new SensitivityNodes(
                        EquipmentsContainer.getEquipmentsContainerUuids(nodeInfos.getMonitoredVoltageLevels()),
                        EquipmentsContainer.getEquipmentsContainerUuids(nodeInfos.getEquipmentsInVoltageRegulation()),
                        EquipmentsContainer.getEquipmentsContainerUuids(nodeInfos.getContingencies()),
                        nodeInfos.isActivated()))
                .toList();

        return SensitivityAnalysisParameters.builder()
                .uuid(paramsInfos.getUuid())
                .provider(paramsInfos.getProvider())
                .flowFlowSensitivityValueThreshold(paramsInfos.getFlowFlowSensitivityValueThreshold())
                .angleFlowSensitivityValueThreshold(paramsInfos.getAngleFlowSensitivityValueThreshold())
                .flowVoltageSensitivityValueThreshold(paramsInfos.getFlowVoltageSensitivityValueThreshold())
                .sensitivityInjectionsSet(sensiInjectionsSets)
                .sensitivityInjection(sensiInjections)
                .sensitivityHVDC(sensiHvdcs)
                .sensitivityPST(sensiPsts)
                .sensitivityNodes(sensiNodes)
                .build();
    }

    public SensitivityAnalysisParametersInfos enrichParametersInfos(SensitivityAnalysisParameters params) {
        Map<UUID, String> allContainerNames = getAllContainerNames(params);

        List<SensitivityInjectionsSetInfos> sensiInjectionsSets = params.getSensitivityInjectionsSet().stream()
                .map(sensitivityInjectionsSet -> new SensitivityInjectionsSetInfos(
                        EquipmentsContainer.enrichEquipmentsContainer(sensitivityInjectionsSet.getMonitoredBranches(), allContainerNames),
                        EquipmentsContainer.enrichEquipmentsContainer(sensitivityInjectionsSet.getInjections(), allContainerNames),
                        sensitivityInjectionsSet.getDistributionType(),
                        EquipmentsContainer.enrichEquipmentsContainer(sensitivityInjectionsSet.getContingencies(), allContainerNames),
                        sensitivityInjectionsSet.isActivated()))
                .toList();

        List<SensitivityInjectionInfos> sensiInjections = params.getSensitivityInjection().stream()
                .map(sensitivityInjection -> new SensitivityInjectionInfos(
                        EquipmentsContainer.enrichEquipmentsContainer(sensitivityInjection.getMonitoredBranches(), allContainerNames),
                        EquipmentsContainer.enrichEquipmentsContainer(sensitivityInjection.getInjections(), allContainerNames),
                        EquipmentsContainer.enrichEquipmentsContainer(sensitivityInjection.getContingencies(), allContainerNames),
                        sensitivityInjection.isActivated()))
                .toList();

        List<SensitivityHvdcInfos> sensiHvdcs = params.getSensitivityHVDC().stream()
                .map(sensitivityHvdc -> new SensitivityHvdcInfos(
                        EquipmentsContainer.enrichEquipmentsContainer(sensitivityHvdc.getMonitoredBranches(), allContainerNames),
                        sensitivityHvdc.getSensitivityType(),
                        EquipmentsContainer.enrichEquipmentsContainer(sensitivityHvdc.getHvdcs(), allContainerNames),
                        EquipmentsContainer.enrichEquipmentsContainer(sensitivityHvdc.getContingencies(), allContainerNames),
                        sensitivityHvdc.isActivated()))
                .toList();

        List<SensitivityPstInfos> sensiPsts = params.getSensitivityPST().stream()
                .map(sensitivityPst -> new SensitivityPstInfos(
                        EquipmentsContainer.enrichEquipmentsContainer(sensitivityPst.getMonitoredBranches(), allContainerNames),
                        sensitivityPst.getSensitivityType(),
                        EquipmentsContainer.enrichEquipmentsContainer(sensitivityPst.getPsts(), allContainerNames),
                        EquipmentsContainer.enrichEquipmentsContainer(sensitivityPst.getContingencies(), allContainerNames),
                        sensitivityPst.isActivated()))
                .toList();

        List<SensitivityNodesInfos> sensiNodes = params.getSensitivityNodes().stream()
                .map(sensitivityNode -> new SensitivityNodesInfos(
                        EquipmentsContainer.enrichEquipmentsContainer(sensitivityNode.getMonitoredVoltageLevels(), allContainerNames),
                        EquipmentsContainer.enrichEquipmentsContainer(sensitivityNode.getEquipmentsInVoltageRegulation(), allContainerNames),
                        EquipmentsContainer.enrichEquipmentsContainer(sensitivityNode.getContingencies(), allContainerNames),
                        sensitivityNode.isActivated()))
                .toList();

        return SensitivityAnalysisParametersInfos.builder()
                .uuid(params.getUuid())
                .provider(params.getProvider())
                .flowFlowSensitivityValueThreshold(params.getFlowFlowSensitivityValueThreshold())
                .angleFlowSensitivityValueThreshold(params.getAngleFlowSensitivityValueThreshold())
                .flowVoltageSensitivityValueThreshold(params.getFlowVoltageSensitivityValueThreshold())
                .sensitivityInjectionsSet(sensiInjectionsSets)
                .sensitivityInjection(sensiInjections)
                .sensitivityHVDC(sensiHvdcs)
                .sensitivityPST(sensiPsts)
                .sensitivityNodes(sensiNodes)
                .build();
    }

    private Map<UUID, String> getAllContainerNames(SensitivityAnalysisParameters params) {
        Set<UUID> allContainerIds = new HashSet<>();

        params.getSensitivityInjectionsSet().forEach(sensitivityInjectionsSet -> {
            allContainerIds.addAll(sensitivityInjectionsSet.getMonitoredBranches());
            allContainerIds.addAll(sensitivityInjectionsSet.getInjections());
            allContainerIds.addAll(sensitivityInjectionsSet.getContingencies());
        });

        params.getSensitivityInjection().forEach(sensitivityInjection -> {
            allContainerIds.addAll(sensitivityInjection.getMonitoredBranches());
            allContainerIds.addAll(sensitivityInjection.getInjections());
            allContainerIds.addAll(sensitivityInjection.getContingencies());
        });

        params.getSensitivityHVDC().forEach(sensitivityHvdc -> {
            allContainerIds.addAll(sensitivityHvdc.getMonitoredBranches());
            allContainerIds.addAll(sensitivityHvdc.getHvdcs());
            allContainerIds.addAll(sensitivityHvdc.getContingencies());
        });

        params.getSensitivityPST().forEach(sensitivityPst -> {
            allContainerIds.addAll(sensitivityPst.getMonitoredBranches());
            allContainerIds.addAll(sensitivityPst.getPsts());
            allContainerIds.addAll(sensitivityPst.getContingencies());
        });

        params.getSensitivityNodes().forEach(sensitivityNode -> {
            allContainerIds.addAll(sensitivityNode.getMonitoredVoltageLevels());
            allContainerIds.addAll(sensitivityNode.getEquipmentsInVoltageRegulation());
            allContainerIds.addAll(sensitivityNode.getContingencies());
        });

        return directoryService.getElementNames(allContainerIds, false);
    }
}
