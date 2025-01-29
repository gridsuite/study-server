package org.gridsuite.study.server.utils;

import org.gridsuite.study.server.repository.rootnetwork.RootNetworkRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class StudyTestUtils {
    private final RootNetworkRepository rootNetworkRepository;

    public StudyTestUtils(RootNetworkRepository rootNetworkRepository) {
        this.rootNetworkRepository = rootNetworkRepository;
    }

    @Transactional
    public UUID getStudyFirstRootNetworkUuid(UUID studyUuid) {
        return rootNetworkRepository.findAllByStudyId(studyUuid).get(0).getId();
    }
}
