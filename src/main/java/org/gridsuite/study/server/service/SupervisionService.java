package org.gridsuite.study.server.service;

import org.gridsuite.study.server.dto.supervision.SupervisionStudyInfos;
import org.gridsuite.study.server.repository.StudyEntity;
import org.gridsuite.study.server.repository.StudyRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class SupervisionService {
    private final StudyRepository studyRepository;

    public SupervisionService(StudyRepository studyRepository) {
        this.studyRepository = studyRepository;
    }

    public List<SupervisionStudyInfos> getStudies() {
        return studyRepository.findAll().stream()
            .map(SupervisionService::toSupervisionStudyInfos)
            .collect(Collectors.toList());
    }

    private static SupervisionStudyInfos toSupervisionStudyInfos(StudyEntity entity) {
        return SupervisionStudyInfos.builder()
            .id(entity.getId())
            .caseFormat(entity.getCaseFormat())
            .networkUuid(entity.getNetworkUuid())
            .build();
    }

}
