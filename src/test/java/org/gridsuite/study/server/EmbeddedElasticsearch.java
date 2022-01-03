/*
  Copyright (c) 2021, RTE (http://www.rte-france.com)
  This Source Code Form is subject to the terms of the Mozilla Public
  License, v. 2.0. If a copy of the MPL was not distributed with this
  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.testcontainers.elasticsearch.ElasticsearchContainer;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

/**
 * A class to launch an embedded DB elasticsearch
 *
 * @author Slimane Amar <slimane.amar at rte-france.com>
 */
@Component
public class EmbeddedElasticsearch {

    private static final String ES_DOCKER_IMAGE_NAME = "docker.elastic.co/elasticsearch/elasticsearch";
    private static final String ES_DOCKER_IMAGE_VERSION = "7.9.3";

    private static ElasticsearchContainer elasticsearchContainer;

    @Value("${spring.data.elasticsearch.enabled:false}")
    boolean elasticsearchEnabled;

    @PostConstruct
    public void postConstruct() {
        if (elasticsearchContainer != null || !elasticsearchEnabled) {
            return;
        }

        elasticsearchContainer = new ElasticsearchContainer(String.format("%s:%s", ES_DOCKER_IMAGE_NAME, ES_DOCKER_IMAGE_VERSION));
        elasticsearchContainer.start();

        System.setProperty("spring.data.elasticsearch.embedded", Boolean.toString(true));
        System.setProperty("spring.data.elasticsearch.embedded.port", Integer.toString(elasticsearchContainer.getMappedPort(9200)));
    }

    @PreDestroy
    private void preDestroy() {
        if (elasticsearchContainer == null) {
            return;
        }

        elasticsearchContainer.stop();
        elasticsearchContainer = null;
    }
}
