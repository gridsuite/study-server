package org.gridsuite.study.server.repository;

import org.springframework.data.cassandra.repository.ReactiveCassandraRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

/**
 * @author Abdelsalem Hedhili <abdelsalem.hedhili at rte-france.com>
 */

@Repository
public interface StudyBySubjectRepository extends ReactiveCassandraRepository<StudyBySubject, Integer> {

    Flux<StudyBySubject> findAllBySubject(String subject);

}
