/**
 * Copyright (c) 2021, All partners of the iTesla project (http://www.itesla-project.eu/consortium)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.dto.modification;

import com.powsybl.commons.PowsyblException;
import io.swagger.v3.oas.annotations.media.Schema;
import org.gridsuite.study.server.StudyException;

/**
 * @author Slimane Amar <slimane.amar at rte-france.com>
 */
@Schema(type = "string", allowableValues = { "generators-modification" })
public enum ModificationType {
    EQUIPMENT_ATTRIBUTE_MODIFICATION,
    LOAD_CREATION,
    LOAD_MODIFICATION,
    GENERATOR_MODIFICATION,
    EQUIPMENT_DELETION,
    GENERATOR_CREATION,
    LINE_CREATION,
    TWO_WINDINGS_TRANSFORMER_CREATION,
    SUBSTATION_CREATION,
    VOLTAGE_LEVEL_CREATION,
    LINE_SPLIT_WITH_VOLTAGE_LEVEL,
    LINE_ATTACH_TO_VOLTAGE_LEVEL,
    GROOVY_SCRIPT,
    BRANCH_STATUS,
    SHUNT_COMPENSATOR_CREATION,
    LINES_ATTACH_TO_SPLIT_LINES,
    LOAD_SCALING,
    DELETE_VOLTAGE_LEVEL_ON_LINE,
    DELETE_ATTACHING_LINE;

    public static String getUriFromType(ModificationType modificationType) {
        switch (modificationType) {
            case LOAD_CREATION:
                return "loads";
            case LOAD_MODIFICATION:
                return "loads-modification";
            case GENERATOR_CREATION:
                return "generators";
            case LINE_CREATION:
                return "lines";
            case TWO_WINDINGS_TRANSFORMER_CREATION:
                return "two-windings-transformers";
            case SUBSTATION_CREATION:
                return "substations";
            case SHUNT_COMPENSATOR_CREATION:
                return "shunt-compensators";
            case VOLTAGE_LEVEL_CREATION:
                return "voltage-levels";
            case LINE_SPLIT_WITH_VOLTAGE_LEVEL:
                return "line-splits";
            case LINE_ATTACH_TO_VOLTAGE_LEVEL:
                return "line-attach";
            case GENERATOR_MODIFICATION:
                return "generators-modification";
            case LINES_ATTACH_TO_SPLIT_LINES:
                return "lines-attach-to-split-lines";
            case LOAD_SCALING:
                return "load-scaling";
            case DELETE_VOLTAGE_LEVEL_ON_LINE:
                return "delete-voltage-level-on-line";
            case DELETE_ATTACHING_LINE:
                return "delete-attaching-line";
            default:
                throw new PowsyblException("Argument " + modificationType + " not expected !!");
        }
    }

    public static ModificationType getTypeFromUri(String uri) {
        switch (uri) {
            case "generators-modification":
                return GENERATOR_MODIFICATION;
            default:
                throw new IllegalArgumentException("Enum unknown entry");
        }
    }

    public static StudyException.Type getExceptionFromType(ModificationType modificationType) {
        switch (modificationType) {
            case LOAD_CREATION:
                return StudyException.Type.LOAD_CREATION_FAILED;
            case LOAD_MODIFICATION:
                return StudyException.Type.LOAD_MODIFICATION_FAILED;
            case GENERATOR_CREATION:
                return StudyException.Type.GENERATOR_CREATION_FAILED;
            case SHUNT_COMPENSATOR_CREATION:
                return StudyException.Type.SHUNT_COMPENSATOR_CREATION_FAILED;
            case LINE_CREATION:
                return StudyException.Type.LINE_CREATION_FAILED;
            case TWO_WINDINGS_TRANSFORMER_CREATION:
                return StudyException.Type.TWO_WINDINGS_TRANSFORMER_CREATION_FAILED;
            case SUBSTATION_CREATION:
                return StudyException.Type.SUBSTATION_CREATION_FAILED;
            case VOLTAGE_LEVEL_CREATION:
                return StudyException.Type.VOLTAGE_LEVEL_CREATION_FAILED;
            case LINE_SPLIT_WITH_VOLTAGE_LEVEL:
                return StudyException.Type.LINE_SPLIT_FAILED;
            case LINE_ATTACH_TO_VOLTAGE_LEVEL:
                return StudyException.Type.LINE_ATTACH_FAILED;
            case GENERATOR_MODIFICATION:
                return StudyException.Type.GENERATOR_MODIFICATION_FAILED;
            case LINES_ATTACH_TO_SPLIT_LINES:
                return StudyException.Type.LINES_ATTACH_TO_SPLIT_LINES;
            case DELETE_VOLTAGE_LEVEL_ON_LINE:
                return StudyException.Type.DELETE_VOLTAGE_LEVEL_ON_LINE;
            case BRANCH_STATUS:
                return StudyException.Type.BRANCH_STATUS_FAILED;
            case LOAD_SCALING:
                return  StudyException.Type.LOAD_SCALING_FAILED;
            case DELETE_ATTACHING_LINE:
                return StudyException.Type.DELETE_ATTACHING_LINE;
            default:
                throw new PowsyblException("Argument " + modificationType + " not expected !!");
        }
    }
}
