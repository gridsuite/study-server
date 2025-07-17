/*
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.dto.sequence;

import org.gridsuite.study.server.networkmodificationtree.entities.NetworkModificationNodeType;

/**
 * @author Kevin Le Saulnier <kevin.lesaulnier at rte-france.com>
 */
public final class SecuritySequence {
    public static final String N_NODE_NAME = "N";
    public static final String NMK_NODE_NAME = "N-K";
    public static final String CURATIF_NODE_NAME = "Curatif";

    private SecuritySequence() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    public static NodeTemplate buildSequence() {
        NodeTemplate nNode = new NodeTemplate(N_NODE_NAME, NetworkModificationNodeType.SECURITY);
        NodeTemplate nmKNode = new NodeTemplate(NMK_NODE_NAME, NetworkModificationNodeType.SECURITY);
        NodeTemplate curNode = new NodeTemplate(CURATIF_NODE_NAME, NetworkModificationNodeType.SECURITY);

        nmKNode.addChild(curNode);
        nNode.addChild(nmKNode);

        return nNode;
    }
}
