/**
 * Copyright (c) 2021, All partners of the iTesla project (http://www.itesla-project.eu/consortium)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.dto.modification;

import com.powsybl.commons.PowsyblException;
import org.gridsuite.study.server.StudyException;

/**
 * @author Slimane Amar <slimane.amar at rte-france.com>
 */
public enum ModificationType {
    EQUIPMENT_ATTRIBUTE_MODIFICATION,
    LOAD_CREATION,
    EQUIPMENT_DELETION,
    GENERATOR_CREATION,
    LINE_CREATION,
    TWO_WINDINGS_TRANSFORMER_CREATION,
    SUBSTATION_CREATION,
    GROOVY_SCRIPT,
    BRANCH_STATUS;

    public static String getUriFromType(ModificationType modificationType) {
        switch (modificationType) {
            case LOAD_CREATION:
                return "loads";
            case GENERATOR_CREATION:
                return "generators";
            case LINE_CREATION:
                return "lines";
            case TWO_WINDINGS_TRANSFORMER_CREATION:
                return "two-windings-transformer";
            case SUBSTATION_CREATION:
                return "substations";
            default:
                throw new PowsyblException("Argument " + modificationType + " not expected !!");
        }
    }

    public static StudyException.Type getExceptionFromType(ModificationType modificationType) {
        switch (modificationType) {
            case LOAD_CREATION:
                return StudyException.Type.LOAD_CREATION_FAILED;
            case GENERATOR_CREATION:
                return StudyException.Type.GENERATOR_CREATION_FAILED;
            case LINE_CREATION:
                return StudyException.Type.LINE_CREATION_FAILED;
            case TWO_WINDINGS_TRANSFORMER_CREATION:
                return StudyException.Type.TWO_WINDINGS_TRANSFORMER_CREATION_FAILED;
            case SUBSTATION_CREATION:
                return StudyException.Type.SUBSTATION_CREATION_FAILED;
            default:
                throw new PowsyblException("Argument " + modificationType + " not expected !!");
        }
    }
}
