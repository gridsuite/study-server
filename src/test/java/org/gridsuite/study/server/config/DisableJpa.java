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
import org.gridsuite.study.server.repository.nodeactivity.NodeActivityRepository;
import org.gridsuite.study.server.repository.rootnetwork.RootNetworkRepository;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.lang.annotation.*;

import static org.mockito.Mockito.mock;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@TestPropertySource(properties = DisableJpa.DISABLE_PROPERTY_NAME + "=true")
@Import(DisableJpa.MockConfig.class)
public @interface DisableJpa {
    String DISABLE_PROPERTY_NAME = "disablejpa";

    @TestConfiguration
    class MockConfig {
        @Bean
        public StudyRepository studyRepository() {
            return mock(StudyRepository.class);
        }

        @Bean
        public StudyCreationRequestRepository studyCreationRequestRepository() {
            return mock(StudyCreationRequestRepository.class);
        }

        @Bean
        public EventRepository eventRepository() {
            return mock(EventRepository.class);
        }

        @Bean
        public NetworkModificationNodeInfoRepository networkModificationNodeInfoRepository() {
            return mock(NetworkModificationNodeInfoRepository.class);
        }

        @Bean
        public NodeRepository nodeRepository() {
            return mock(NodeRepository.class);
        }

        @Bean
        public RootNodeInfoRepository rootNodeInfoRepository() {
            return mock(RootNodeInfoRepository.class);
        }

        @Bean
        public RootNetworkRepository rootNetworkRepository() {
            return mock(RootNetworkRepository.class);
        }

        @Bean
        public NodeActivityRepository nodeActivityRepository() {
            return mock(NodeActivityRepository.class);
        }

        @Bean
        public EntityManagerFactory entityManagerFactory() {
            return mock(EntityManagerFactory.class);
        }

        @Bean(name = "jpaSharedEM_entityManagerFactory")
        public EntityManagerFactory jpaSharedEntityManagerFactory() {
            return entityManagerFactory();
        }
    }
}
