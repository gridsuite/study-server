/*
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.dto.sequence;

/**
 * @author Kevin Le Saulnier <kevin.lesaulnier at rte-france.com>
 */
public enum NodeSequenceType {
    SECURITY_SEQUENCE;

    public NodeTemplate getNodeSequence() {
        return switch (this) {
            case SECURITY_SEQUENCE -> SecuritySequence.buildSequence();
        };
    }
}
