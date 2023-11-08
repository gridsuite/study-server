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

/**
 * This class describes an element type network impact
 * This type of network impact only describes an individual impacted item and the list of associated substations
 *
 * @author Slimane Amar <slimane.amar at rte-france.com>
 * @author Sylvain Bouzols <sylvain.bouzols at rte-france.com>
 */
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Setter
@Getter
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString
public class SimpleElementImpact extends AbstractBaseImpact {

    /** The impacted element ID */
    private String elementId;

    /** The impacted substations IDs */
    private Set<String> substationIds;
}
