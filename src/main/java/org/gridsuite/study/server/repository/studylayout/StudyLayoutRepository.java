package org.gridsuite.study.server.repository.studylayout;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StudyLayoutRepository extends JpaRepository<StudyLayoutEntity, StudyLayoutKey> {
}
