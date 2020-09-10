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
public interface PublicStudyRepository extends ReactiveCassandraRepository<PublicStudy, Integer> {

    Flux<PublicStudy> findAll();

    @Query("SELECT * FROM publicStudy WHERE studyname = :studyName and userId = :userId")
    Mono<PublicStudy> findById(@Param("studyName") String studyName, @Param("userId") UserId userId);

    @Query("DELETE FROM publicStudy WHERE studyname = :studyName and userId = :userId")
    Mono<Void> delete(@Param("studyName") String studyName, @Param("userId") UserId userId);

}
