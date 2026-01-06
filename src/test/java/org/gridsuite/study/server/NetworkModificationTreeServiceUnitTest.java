package org.gridsuite.study.server;

import org.gridsuite.study.server.networkmodificationtree.entities.NodeEntity;
import org.gridsuite.study.server.networkmodificationtree.entities.NodeType;
import org.gridsuite.study.server.repository.networkmodificationtree.NodeRepository;
import org.gridsuite.study.server.service.NetworkModificationTreeService;
import org.gridsuite.study.server.utils.elasticsearch.DisableElasticsearch;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DisableElasticsearch
class NetworkModificationTreeServiceUnitTest {

    @Autowired
    private NodeRepository nodeRepository;
    @Autowired
    private NetworkModificationTreeService networkModificationTreeService;

    NodeEntity rootNode;
    NodeEntity node1;
    NodeEntity node2;
    NodeEntity node3;
    NodeEntity node4;

    @Test
    void testGetHighestNodeUuidOfSameSubtree() {
        createNodeTree();
        assertThat(networkModificationTreeService.getHighestNodeUuids(node1.getIdNode(), node2.getIdNode()))
            .usingRecursiveComparison()
            .isEqualTo(List.of(node1.getIdNode()));

        assertThat(networkModificationTreeService.getHighestNodeUuids(node1.getIdNode(), node4.getIdNode()))
            .usingRecursiveComparison()
            .isEqualTo(List.of(node1.getIdNode()));
    }

    @Test
    void testGetHighestNodeUuidOfDifferentSubtree() {
        createNodeTree();
        assertThat(networkModificationTreeService.getHighestNodeUuids(node2.getIdNode(), node3.getIdNode()))
            .usingRecursiveComparison()
            .isEqualTo(List.of(node2.getIdNode(), node3.getIdNode()));

        assertThat(networkModificationTreeService.getHighestNodeUuids(node2.getIdNode(), node4.getIdNode()))
            .usingRecursiveComparison()
            .isEqualTo(List.of(node2.getIdNode(), node4.getIdNode()));
    }

    @Test
    void testGetHighestNodeUuidOfSameUuids() {
        UUID randomUuid = UUID.randomUUID();
        assertThat(networkModificationTreeService.getHighestNodeUuids(randomUuid, randomUuid))
            .usingRecursiveComparison()
            .isEqualTo(List.of(randomUuid));
    }

    private void createNodeTree() {
        /*
                    root
                     |
                     N1
                   ------
                   |    |
                   N2   N3
                        |
                        N4
         */
        rootNode = nodeRepository.save(new NodeEntity(null, null, NodeType.ROOT, null, false, null));
        node1 = nodeRepository.save(new NodeEntity(null, rootNode, NodeType.NETWORK_MODIFICATION, null, false, null));
        node2 = nodeRepository.save(new NodeEntity(null, node1, NodeType.NETWORK_MODIFICATION, null, false, null));
        node3 = nodeRepository.save(new NodeEntity(null, node1, NodeType.NETWORK_MODIFICATION, null, false, null));
        node4 = nodeRepository.save(new NodeEntity(null, node3, NodeType.NETWORK_MODIFICATION, null, false, null));
    }
}
