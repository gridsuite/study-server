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
public interface StudyBySubjectRepository extends ReactiveCassandraRepository<StudyBySubject, Integer> {

    Flux<StudyBySubject> findAllByUserIdAndIsPrivate(UserId userId, boolean isPrivate);

    @Query("SELECT * FROM studybysubject WHERE userId = :userId and isprivate = :isPrivate and studyname = :studyName")
    Mono<StudyBySubject> findById(@Param("userId") UserId userId, @Param("isPrivate") boolean isPrivate, @Param("studyName") String studyName);

    @Query("DELETE FROM studybysubject WHERE userId = :userId and isprivate = :isPrivate and studyname = :studyName")
    Mono<Void> delete(@Param("userId") UserId userId, @Param("isPrivate") boolean isPrivate, @Param("studyName") String studyName);
}
