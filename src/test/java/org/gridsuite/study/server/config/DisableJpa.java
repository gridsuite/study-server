/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.config;

import jakarta.persistence.EntityManagerFactory;
import org.gridsuite.study.server.repository.StudyCreationRequestRepository;
import org.gridsuite.study.server.repository.StudyRepository;
import org.gridsuite.study.server.repository.dynamicsimulation.EventRepository;
import org.gridsuite.study.server.repository.networkmodificationtree.NetworkModificationNodeInfoRepository;
import org.gridsuite.study.server.repository.networkmodificationtree.NodeRepository;
import org.gridsuite.study.server.repository.networkmodificationtree.RootNodeInfoRepository;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;

import java.lang.annotation.*;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@MockBean(classes = EntityManagerFactory.class, name = "jpaSharedEM_entityManagerFactory")
@MockBean(StudyRepository.class)
@MockBean(StudyCreationRequestRepository.class)
@MockBean(EventRepository.class)
@MockBean(NetworkModificationNodeInfoRepository.class)
@MockBean(NodeRepository.class)
@MockBean(RootNodeInfoRepository.class)
//@MockBean(NetworkModificationTreeService.class) //TODO mockBean NetworkModificationNodeInfoRepository not found ?
@TestPropertySource(properties = DisableJpa.PROPERTY_NAME + "=false") //can't have multiple @EnableAutoConfiguration(exclude)
public @interface DisableJpa {
    String PROPERTY_NAME = "test.enable.jpa";
}
