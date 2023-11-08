/**
  Copyright (c) 2023, All partners of the iTesla project (http://www.itesla-project.eu/consortium)
  This Source Code Form is subject to the terms of the Mozilla Public
  License, v. 2.0. If a copy of the MPL was not distributed with this
  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.dto.impacts;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.powsybl.iidm.network.IdentifiableType;

import lombok.*;
import lombok.experimental.SuperBuilder;

/**
 * This class describes a base impact
 * This is the base type of all network impacts
 *
 * @author Sylvain Bouzols <sylvain.bouzols at rte-france.com>
 */
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.EXISTING_PROPERTY,
    property = "impactType",
    visible = true
)
@JsonSubTypes({
    @JsonSubTypes.Type(value = SimpleElementImpact.class, names = { "CREATION", "MODIFICATION", "DELETION" }),
    @JsonSubTypes.Type(value = CollectionElementImpact.class, name = "COLLECTION")
})
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Setter
@Getter
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false)
@ToString
public abstract class AbstractBaseImpact {

    public enum ImpactType {
        CREATION,
        MODIFICATION,
        DELETION,
        COLLECTION
    }

    private ImpactType impactType;

    private IdentifiableType elementType;
}
