/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.study.server;

/**
 * @author Abdelsalem Hedhili <abdelsalem.hedhili at rte-france.com>
 */

final class StudyConstants {

    private StudyConstants() {

    }

    static final String CASE_API_VERSION = "v1";
    static final String VOLTAGE_LEVEL_DIAGRAM_API_VERSION = "v1";
    static final String IIDM_CONVERTER_API_VERSION = "v1";
    static final String GEO_DATA_API_VERSION = "v1";

    static final String CASE_PARAM = "caseName";
    static final String NETWORK_UUID = "networkUuid";

    static final String STUDY_ALREADY_EXISTS = "STUDY ALREADY_EXISTS";
    static final String CASE_DOESNT_EXISTS = "CASE DOESN'T EXISTS";
    static final String CASE_ALREADY_EXISTS = "CASE ALREADY EXISTS";


}
