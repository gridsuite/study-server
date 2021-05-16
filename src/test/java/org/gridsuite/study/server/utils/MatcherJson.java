/*
  Copyright (c) 2021, RTE (http://www.rte-france.com)
  This Source Code Form is subject to the terms of the Mozilla Public
  License, v. 2.0. If a copy of the MPL was not distributed with this
  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;

/**
 * @author Slimane Amar <slimane.amar at rte-france.com>
 */
public class MatcherJson<T> extends TypeSafeMatcher<T> {
    T reference;

    public MatcherJson(T val) {
        this.reference = val;
    }

    @SneakyThrows
    @Override
    public boolean matchesSafely(T s) {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.writeValueAsString(reference).equals(objectMapper.writeValueAsString(s));
    }

    @Override
    public void describeTo(Description description) {
        description.appendValue(reference);
    }
}
