/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.repository.networkmodificationtree;

import org.gridsuite.study.server.networkmodificationtree.entities.AbstractNodeInfoEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * @author Jacques Borsenberger <jacques.borsenberger at rte-france.com>
 */
@NoRepositoryBean
public interface NodeInfoRepository<T extends AbstractNodeInfoEntity> extends JpaRepository<T, UUID> {
    void deleteByIdNodeIn(Collection<UUID> ids);

    List<T> findAllByNodeStudyId(UUID studyId);
}
