package org.gridsuite.study.server.dto.sequence;

import lombok.Getter;
import lombok.Setter;
import org.gridsuite.study.server.networkmodificationtree.dto.NetworkModificationNode;
import org.gridsuite.study.server.networkmodificationtree.entities.NetworkModificationNodeType;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Getter
@Setter
public class NodeTemplate {
    private String name;
    private NetworkModificationNodeType networkModificationNodeType;
    private List<NodeTemplate> childrenNode = new ArrayList<>();

    public NodeTemplate(String name, NetworkModificationNodeType networkModificationNodeType) {
        this.name = name;
        this.networkModificationNodeType = networkModificationNodeType;
    }

    public void addChild(NodeTemplate child) {
        childrenNode.add(child);
    }

    public NetworkModificationNode toNetworkModificationNodeTree() {
        return NetworkModificationNode.builder()
            .name(name)
            .nodeType(networkModificationNodeType)
            .children(childrenNode.stream().map(NodeTemplate::toNetworkModificationNodeTree).collect(Collectors.toList()))
            .build();
    }
}
