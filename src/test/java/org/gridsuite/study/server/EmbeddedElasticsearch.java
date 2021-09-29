/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server;

import org.elasticsearch.action.admin.cluster.node.info.NodeInfo;
import org.elasticsearch.action.admin.cluster.node.info.NodesInfoRequestBuilder;
import org.elasticsearch.action.admin.cluster.node.info.NodesInfoResponse;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.BoundTransportAddress;
import org.elasticsearch.common.transport.TransportAddress;
import org.elasticsearch.http.HttpInfo;
import org.elasticsearch.index.reindex.ReindexPlugin;
import org.elasticsearch.node.Node;
import org.elasticsearch.node.NodeValidationException;
import org.elasticsearch.transport.Netty4Plugin;
import org.junit.rules.ExternalResource;
import org.springframework.data.elasticsearch.client.NodeClientFactoryBean;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * A class to launch an embedded DB elasticsearch
 *
 * @author Slimane Amar <slimane.amar at rte-france.com>
 */
@Component
public class EmbeddedElasticsearch extends ExternalResource {

    public EmbeddedElasticsearch() throws NodeValidationException {
        String pathHome = "src/test/resources/test-home-dir";
        String pathData = "target/elasticsearchTestData";
        String clusterName = UUID.randomUUID().toString();

        Node node = new NodeClientFactoryBean.TestNode(
                Settings.builder()
                        .put("transport.type", "netty4")
                        .put("discovery.type", "single-node")
                        .put("http.type", "netty4")
                        .put("path.home", pathHome)
                        .put("path.data", pathData)
                        .put("cluster.name", clusterName)
                        .put("node.max_local_storage_nodes", 100)
                        // the following 3 settings are needed to avoid problems on big, but
                        // almost full filesystems, see DATAES-741
                        .put("cluster.routing.allocation.disk.watermark.low", "1gb")
                        .put("cluster.routing.allocation.disk.watermark.high", "1gb")
                        .put("cluster.routing.allocation.disk.watermark.flood_stage", "1gb")
                        .build(),
                List.of(Netty4Plugin.class, ReindexPlugin.class));

        node.start();

        publishNodePort(node);
    }

    private void publishNodePort(Node node) {
        NodesInfoRequestBuilder nodesInfoRequestBuilder = node.client().admin().cluster().prepareNodesInfo();
        NodesInfoResponse nodesInfoResponse = nodesInfoRequestBuilder.get();
        List<NodeInfo> nodeInfos = nodesInfoResponse.getNodes();
        NodeInfo nodeInfo = nodeInfos.get(0);
        HttpInfo httpInfo = nodeInfo.getInfo(HttpInfo.class);
        BoundTransportAddress boundTransportAddress = httpInfo.address();
        TransportAddress transportAddress = boundTransportAddress.publishAddress();
        System.setProperty("spring.data.elasticsearch.embedded", Boolean.toString(true));
        System.setProperty("spring.data.elasticsearch.embedded.port", Integer.toString(transportAddress.getPort()));
    }
}
