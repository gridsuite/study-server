package org.gridsuite.study.server.dto.sequence;

import org.gridsuite.study.server.networkmodificationtree.entities.NetworkModificationNodeType;

public enum NodeSequenceType {
    SECURITY_SEQUENCE;

    public NodeTemplate getNodeSequence() {
        return switch (this) {
            case SECURITY_SEQUENCE -> buildSecuritySequence();
        };
    }

    private NodeTemplate buildSecuritySequence() {
        NodeTemplate nNode = new NodeTemplate("N", NetworkModificationNodeType.SECURITY);
        NodeTemplate nmKNode = new NodeTemplate("NmK", NetworkModificationNodeType.SECURITY);
        NodeTemplate curNode = new NodeTemplate("Cur", NetworkModificationNodeType.SECURITY);

        nmKNode.addChild(curNode);
        nNode.addChild(nmKNode);

        return nNode;
    }
}
