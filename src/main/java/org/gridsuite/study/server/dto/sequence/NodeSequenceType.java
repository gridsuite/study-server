package org.gridsuite.study.server.dto.sequence;

import org.gridsuite.study.server.networkmodificationtree.entities.NetworkModificationNodeType;

public enum NodeSequenceType {
    SECURITY_SEQUENCE;

    public static final String N_NODE_NAME = "N";
    public static final String NMK_NODE_NAME = "N-K";
    public static final String CURATIF_NODE_NAME = "Curatif";

    public NodeTemplate getNodeSequence() {
        return switch (this) {
            case SECURITY_SEQUENCE -> buildSecuritySequence();
        };
    }

    private NodeTemplate buildSecuritySequence() {
        NodeTemplate nNode = new NodeTemplate(N_NODE_NAME, NetworkModificationNodeType.SECURITY);
        NodeTemplate nmKNode = new NodeTemplate(NMK_NODE_NAME, NetworkModificationNodeType.SECURITY);
        NodeTemplate curNode = new NodeTemplate(CURATIF_NODE_NAME, NetworkModificationNodeType.SECURITY);

        nmKNode.addChild(curNode);
        nNode.addChild(nmKNode);

        return nNode;
    }
}
