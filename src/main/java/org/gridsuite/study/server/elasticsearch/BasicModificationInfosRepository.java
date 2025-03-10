package org.gridsuite.study.server.elasticsearch;

import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

import java.util.List;
import java.util.UUID;

public interface BasicModificationInfosRepository extends ElasticsearchRepository<BasicModificationInfos, String> {
    void deleteAllByNetworkUuidAndGroupUuidIn(UUID networkUuid, List<UUID> groupUuid);
}
