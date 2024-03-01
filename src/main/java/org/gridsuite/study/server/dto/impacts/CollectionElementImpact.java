/**
  Copyright (c) 2024, All partners of the iTesla project (http://www.itesla-project.eu/consortium)
  This Source Code Form is subject to the terms of the Mozilla Public
  License, v. 2.0. If a copy of the MPL was not distributed with this
  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.dto.impacts;

import lombok.*;
import lombok.experimental.SuperBuilder;

/**
 * This class describes a collection type network impact
 * This type of network impact describes an impact on multiple items and the list of associated substations
 *
 * @author Sylvain Bouzols <sylvain.bouzols at rte-france.com>
 */
@SuperBuilder
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class CollectionElementImpact extends AbstractBaseImpact {
    @Override
    public ImpactType getType() {
        return ImpactType.COLLECTION;
    }

    public boolean isSimple() {
        return false;
    }

    public boolean isCollection() {
        return true;
    }
}
