package org.gridsuite.study.server.elasticsearch;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class BasicModificationInfosService {
    private final BasicModificationInfosRepository basicModificationInfosRepository;

    public BasicModificationInfosService(BasicModificationInfosRepository basicModificationInfosRepository) {
        this.basicModificationInfosRepository = basicModificationInfosRepository;
    }

    public void deleteByGroupUuidsAndNetworkUuid(List<UUID> groupUuids, UUID networkUuid) {
        basicModificationInfosRepository.deleteAllByNetworkUuidAndGroupUuidIn(networkUuid, groupUuids);
    }
}
