package org.gridsuite.study.server.service;

import org.gridsuite.study.server.networkmodificationtree.entities.NetworkModificationNodeInfoEntity;
import org.gridsuite.study.server.repository.networkmodificationtree.NetworkModificationNodeInfoRepository;
import org.gridsuite.study.server.service.dynamicsimulation.DynamicSimulationService;
import org.gridsuite.study.server.service.shortcircuit.ShortCircuitService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpStatusCodeException;

import java.util.List;
import java.util.UUID;

import static org.gridsuite.study.server.StudyException.Type.SUPERVSION_ERROR_DELETE_RESULTS;
import static org.gridsuite.study.server.utils.StudyUtils.handleHttpError;

@Service
public class SupervisionService {
    @Autowired
    private ReportService reportService;

    @Autowired
    private LoadFlowService loadFlowService;

    @Autowired
    private DynamicSimulationService dynamicSimulationService;

    @Autowired
    private SecurityAnalysisService securityAnalysisService;

    @Autowired
    private SensitivityAnalysisService sensitivityAnalysisService;

    @Autowired
    private ShortCircuitService shortCircuitService;

    @Autowired
    private VoltageInitService voltageInitService;

    private final NetworkModificationNodeInfoRepository networkModificationNodeInfoRepository;

    public SupervisionService(NetworkModificationNodeInfoRepository networkModificationNodeInfoRepository) {
        this.networkModificationNodeInfoRepository = networkModificationNodeInfoRepository;
    }

    @Transactional
    public void deleteLoadflowResults() {
        List<NetworkModificationNodeInfoEntity> nodes = networkModificationNodeInfoRepository.findAllByLoadFlowResultUuidNotNull();
        nodes.stream().forEach(node -> {
            node.setLoadFlowResultUuid(null);
            networkModificationNodeInfoRepository.save(node);
        });
        List<UUID> reportsIds = nodes.stream().map(NetworkModificationNodeInfoEntity::getReportUuid).toList();
        try {
            reportService.deleteSubreport(reportsIds, "loadflow");
            loadFlowService.deleteLoadFlowResults();
        } catch (HttpStatusCodeException e) {
            throw handleHttpError(e, SUPERVSION_ERROR_DELETE_RESULTS);
        }
    }

    @Transactional
    public void deleteDynamicSimulationResults() {
        List<NetworkModificationNodeInfoEntity> nodes = networkModificationNodeInfoRepository.findAllByDynamicSimulationResultUuidNotNull();
        nodes.stream().forEach(node -> {
            node.setShortCircuitAnalysisResultUuid(null);
            networkModificationNodeInfoRepository.save(node);
        });
        try {
            //TODO Add logs deletion once they are added
            dynamicSimulationService.deleteResults();
        } catch (HttpStatusCodeException e) {
            throw handleHttpError(e, SUPERVSION_ERROR_DELETE_RESULTS);
        }
    }

    @Transactional
    public void deleteSecurityAnalysisResults() {
        List<NetworkModificationNodeInfoEntity> nodes = networkModificationNodeInfoRepository.findAllBySecurityAnalysisResultUuidNotNull();
        nodes.stream().forEach(node -> {
            node.setSecurityAnalysisResultUuid(null);
            networkModificationNodeInfoRepository.save(node);
        });
        List<UUID> reportsIds = nodes.stream().map(NetworkModificationNodeInfoEntity::getReportUuid).toList();
        try {
            reportService.deleteSubreport(reportsIds, "security-analysis");
            securityAnalysisService.deleteSaResults();
        } catch (HttpStatusCodeException e) {
            throw handleHttpError(e, SUPERVSION_ERROR_DELETE_RESULTS);
        }
    }

    @Transactional
    public void deleteSensitivityAnalysisResults() {
        List<NetworkModificationNodeInfoEntity> nodes = networkModificationNodeInfoRepository.findAllBySensitivityAnalysisResultUuidNotNull();
        nodes.stream().forEach(node -> {
            node.setSensitivityAnalysisResultUuid(null);
            networkModificationNodeInfoRepository.save(node);
        });
        List<UUID> reportsIds = nodes.stream().map(NetworkModificationNodeInfoEntity::getReportUuid).toList();
        try {
            reportService.deleteSubreport(reportsIds, "sensitivity-analysis");
            sensitivityAnalysisService.deleteSensitivityAnalysisResults();
        } catch (HttpStatusCodeException e) {
            throw handleHttpError(e, SUPERVSION_ERROR_DELETE_RESULTS);
        }
    }

    @Transactional
    public void deleteShortcircuitResults() {
        List<NetworkModificationNodeInfoEntity> nodes = networkModificationNodeInfoRepository.findAllByShortCircuitAnalysisResultUuidNotNull();
        nodes.stream().forEach(node -> {
            node.setShortCircuitAnalysisResultUuid(null);
            networkModificationNodeInfoRepository.save(node);
        });
        List<UUID> reportsIds = nodes.stream().map(NetworkModificationNodeInfoEntity::getReportUuid).toList();
        try {
            reportService.deleteSubreport(reportsIds, "shortcircuit");
            shortCircuitService.deleteShortCircuitAnalysisResults();
        } catch (HttpStatusCodeException e) {
            throw handleHttpError(e, SUPERVSION_ERROR_DELETE_RESULTS);
        }
    }

    @Transactional
    public void deleteVoltageInitResults() {
        List<NetworkModificationNodeInfoEntity> nodes = networkModificationNodeInfoRepository.findAllByVoltageInitResultUuidNotNull();
        nodes.stream().forEach(node -> {
            node.setVoltageInitResultUuid(null);
            networkModificationNodeInfoRepository.save(node);
        });
        try {
            //TODO Add logs deletion once they are added
            voltageInitService.deleteVoltageInitResults();
        } catch (HttpStatusCodeException e) {
            throw handleHttpError(e, SUPERVSION_ERROR_DELETE_RESULTS);
        }
    }
}
