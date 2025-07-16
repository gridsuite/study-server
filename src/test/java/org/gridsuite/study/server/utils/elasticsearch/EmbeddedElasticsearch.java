/*
  Copyright (c) 2021, RTE (http://www.rte-france.com)
  This Source Code Form is subject to the terms of the Mozilla Public
  License, v. 2.0. If a copy of the MPL was not distributed with this
  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.utils.elasticsearch;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;
import org.testcontainers.elasticsearch.ElasticsearchContainer;

import java.util.Map;

/**
 * A class to launch an embedded DB elasticsearch
 *
 * @author Slimane Amar <slimane.amar at rte-france.com>
 */
@Component
public class EmbeddedElasticsearch {

    private static final String ES_DOCKER_IMAGE_NAME = "docker.elastic.co/elasticsearch/elasticsearch";
    private static final String ES_DOCKER_IMAGE_VERSION = "8.7.1";

    private static ElasticsearchContainer elasticsearchContainer;

    @PostConstruct
    public void postConstruct() {
        if (elasticsearchContainer != null) {
            return;
        }

        elasticsearchContainer = new ElasticsearchContainer(String.format("%s:%s", ES_DOCKER_IMAGE_NAME, ES_DOCKER_IMAGE_VERSION));
        Map<String, String> envMap = elasticsearchContainer.getEnvMap();
        envMap.put("discovery.type", "single-node");
        envMap.put("LOGSPOUT", "ignore");
        //Els 8 has security enabled by default
        envMap.put("xpack.security.enabled", Boolean.FALSE.toString());
        envMap.put("ingest.geoip.downloader.enabled", Boolean.FALSE.toString());
        envMap.put("ES_JAVA_OPTS", "-Xms128m -Xmx128m");
        envMap.put("action.auto_create_index", "false");
        elasticsearchContainer.start();

        System.setProperty("spring.elasticsearch.uris", "http://localhost:".concat(Integer.toString(elasticsearchContainer.getMappedPort(9200))));
    }
}
