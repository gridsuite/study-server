package org.gridsuite.study.server.service;

import org.gridsuite.study.server.StudyException;
import org.gridsuite.study.server.dto.studylayout.StudyLayout;
import org.gridsuite.study.server.repository.studylayout.StudyLayoutKey;
import org.gridsuite.study.server.repository.studylayout.StudyLayoutRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class StudyLayoutService {
    private final StudyLayoutRepository studyLayoutRepository;

    public StudyLayoutService(StudyLayoutRepository studyLayoutRepository) {
        this.studyLayoutRepository = studyLayoutRepository;
    }

    public StudyLayout getByStudyUuidAndUserId(UUID studyUuid, String userId) {
        return studyLayoutRepository
            .findById(new StudyLayoutKey(studyUuid, userId))
            .orElseThrow(() -> new StudyException(StudyException.Type.STUDY_LAYOUT_NOT_FOUND))
            .toDto();
    }
}
