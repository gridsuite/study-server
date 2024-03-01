/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.dto.impacts;

import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * This class describes an element type network impact
 * This type of network impact only describes an individual impacted item and the list of associated substations
 *
 * @author Slimane Amar <slimane.amar at rte-france.com>
 * @author Sylvain Bouzols <sylvain.bouzols at rte-france.com>
 */
@SuperBuilder
@Data
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class SimpleElementImpact extends AbstractBaseImpact {

    public enum SimpleImpactType {
        CREATION,
        MODIFICATION,
        DELETION
    }

    private SimpleImpactType simpleImpactType;

    /** The impacted element ID */
    private String elementId;

    /** The impacted substations IDs */
    private Set<String> substationIds;

    @Override
    public ImpactType getType() {
        return ImpactType.SIMPLE;
    }

    public boolean isSimple() {
        return true;
    }

    public boolean isCollection() {
        return false;
    }

    @JsonIgnore
    public boolean isCreation() {
        return getSimpleImpactType() == SimpleImpactType.CREATION;
    }

    @JsonIgnore
    public boolean isModification() {
        return getSimpleImpactType() == SimpleImpactType.MODIFICATION;
    }

    @JsonIgnore
    public boolean isDeletion() {
        return getSimpleImpactType() == SimpleImpactType.DELETION;
    }
}
