package org.gridsuite.study.server.service.loadflow;

import com.powsybl.loadflow.LoadFlowParameters;
import org.gridsuite.study.server.dto.LoadFlowParametersInfos;
import org.gridsuite.study.server.error.StudyException;
import org.gridsuite.study.server.repository.StudyEntity;
import org.gridsuite.study.server.repository.StudyRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.gridsuite.study.server.error.StudyBusinessErrorCode.NOT_FOUND;

@Service
public class LoadFlowService {
    private final StudyRepository studyRepository;
    private final LoadFlowServiceRest loadflowServiceRest;

    @Autowired
    public LoadFlowService(StudyRepository studyRepository, LoadFlowServiceRest loadflowServiceRest) {
        this.studyRepository = studyRepository;
        this.loadflowServiceRest = loadflowServiceRest;
    }

    private StudyEntity getStudy(UUID studyUuid) {
        return studyRepository.findById(studyUuid).orElseThrow(() -> new StudyException(NOT_FOUND, "Study not found"));
    }

    @Transactional
    public UUID getLoadFlowParametersId(UUID studyUuid) {
        StudyEntity studyEntity = getStudy(studyUuid);
        return loadflowServiceRest.getLoadFlowParametersOrDefaultsUuid(studyEntity);
    }

    @Transactional
    public String getLoadFlowProvider(UUID studyUuid) {
        StudyEntity studyEntity = getStudy(studyUuid);
        return loadflowServiceRest.getLoadFlowProvider(studyEntity.getLoadFlowParametersUuid());
    }

    @Transactional
    public LoadFlowParametersInfos getLoadFlowParametersInfos(UUID studyUuid) {
        StudyEntity studyEntity = getStudy(studyUuid);
        return getLoadFlowParametersInfos(studyEntity);
    }

    public LoadFlowParametersInfos getLoadFlowParametersInfos(StudyEntity studyEntity) {
        UUID loadFlowParamsUuid = loadflowServiceRest.getLoadFlowParametersOrDefaultsUuid(studyEntity);
        return loadflowServiceRest.getLoadFlowParameters(loadFlowParamsUuid);
    }

    public LoadFlowParameters getLoadFlowParameters(StudyEntity studyEntity) {
        LoadFlowParametersInfos lfParameters = getLoadFlowParametersInfos(studyEntity);
        return lfParameters.getCommonParameters();
    }
}
