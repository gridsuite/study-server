/*
  Copyright (c) 2021, RTE (http://www.rte-france.com)
  This Source Code Form is subject to the terms of the Mozilla Public
  License, v. 2.0. If a copy of the MPL was not distributed with this
  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.utils;

import org.gridsuite.study.server.dto.EquipmentInfos;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;

/**
 * @author Slimane Amar <slimane.amar at rte-france.com>
 */
public class MatcherEquipmentInfos<T extends EquipmentInfos> extends TypeSafeMatcher<T> {
    T reference;

    public MatcherEquipmentInfos(T ref) {
        this.reference = ref;
    }

    @Override
    public boolean matchesSafely(T m) {
        return m.getNetworkUuid().equals(reference.getNetworkUuid())
                && m.getId().equals(reference.getId())
                && m.getEquipmentId().equals(reference.getEquipmentId())
                && m.getEquipmentName().equals(reference.getEquipmentName())
                && m.getEquipmentType().equals(reference.getEquipmentType());
    }

    @Override
    public void describeTo(Description description) {
        description.appendValue(reference);
    }
}
