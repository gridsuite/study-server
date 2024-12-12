/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.dto.caseimport;

import lombok.Getter;

public enum CaseImportAction {
    STUDY_CREATION("Study creation"),
    ROOT_NETWORK_CREATION("Root network creation"),
    NETWORK_RECREATION("Network recreation"),
    ROOT_NETWORK_MODIFICATION("Root network modification");

    @Getter
    public final String label;

    CaseImportAction(String label) {
        this.label = label;
    }
}
