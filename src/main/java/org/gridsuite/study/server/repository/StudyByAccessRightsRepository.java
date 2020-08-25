package org.gridsuite.study.server.repository;

import org.springframework.data.cassandra.repository.ReactiveCassandraRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

/**
 * @author Abdelsalem Hedhili <abdelsalem.hedhili at rte-france.com>
 */

@Repository
public interface StudyByAccessRightsRepository extends ReactiveCassandraRepository<StudyByAccessRights, Integer> {

    Flux<StudyByAccessRights> findAllByIsPrivate(boolean isPrivate);

}
