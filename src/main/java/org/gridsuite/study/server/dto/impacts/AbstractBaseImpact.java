/**
  Copyright (c) 2024, All partners of the iTesla project (http://www.itesla-project.eu/consortium)
  This Source Code Form is subject to the terms of the Mozilla Public
  License, v. 2.0. If a copy of the MPL was not distributed with this
  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.dto.impacts;

import com.fasterxml.jackson.annotation.JsonIgnore;
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
    property = "type",
    visible = true
)
@JsonSubTypes({
    @JsonSubTypes.Type(value = SimpleElementImpact.class, name = "SIMPLE"),
    @JsonSubTypes.Type(value = CollectionElementImpact.class, name = "COLLECTION")
})
@SuperBuilder
@Data
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor
public abstract class AbstractBaseImpact {

    public enum ImpactType {
        SIMPLE,
        COLLECTION
    }

    @Setter(AccessLevel.NONE)
    private ImpactType type;

    private IdentifiableType elementType;

    @JsonIgnore
    public abstract boolean isSimple();

    @JsonIgnore
    public abstract boolean isCollection();
}
