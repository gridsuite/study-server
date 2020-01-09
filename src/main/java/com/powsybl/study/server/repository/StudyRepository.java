/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.study.server.repository;

import org.springframework.data.cassandra.repository.CassandraRepository;
import org.springframework.data.cassandra.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * @author Abdelsalem Hedhili <abdelsalem.hedhili at rte-france.com>
 */

@Repository
public interface StudyRepository extends CassandraRepository<Study, Integer> {

    @Override
    List<Study> findAll();

    Optional<Study> findByName(String studyName);

    @Override
    <S extends Study> S insert(S s);

    @Query("delete from study where studyname = :studyName")
    void deleteByName(@Param("studyName") String studyName);

}
