package org.gridsuite.study.server.repository;

import org.springframework.data.cassandra.repository.Query;
import org.springframework.data.cassandra.repository.ReactiveCassandraRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * @author Abdelsalem Hedhili <abdelsalem.hedhili at rte-france.com>
 */

@Repository
public interface PrivateStudyBySubjectRepository extends ReactiveCassandraRepository<PrivateStudyBySubject, Integer> {

    Flux<PrivateStudyBySubject> findAllByUserId(String userId);

    @Query("DELETE FROM privateStudyBySubject WHERE userId = :userId and studyname = :studyName")
    Mono<Void> delete(@Param("userId") String userId, @Param("studyName") String studyName);
}
