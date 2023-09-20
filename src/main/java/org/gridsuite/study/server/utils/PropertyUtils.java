/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.utils;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;

import java.beans.FeatureDescriptor;
import java.beans.PropertyDescriptor;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * @author Jacques Borsenberger <jacques.borsenberger at rte-france.com
 */
public final class PropertyUtils {

    private PropertyUtils() {
    }

    public static void copyNonNullProperties(Object src, Object target, String... authorizedNullProperties) {
        BeanUtils.copyProperties(src, target, getNullPropertyNames(src, authorizedNullProperties));
    }

    public static String[] getNullPropertyNames(Object source, String... authorizedNullProperties) {
        final BeanWrapper beanSource = new BeanWrapperImpl(source);
        PropertyDescriptor[] propertyDescriptors = beanSource.getPropertyDescriptors();

        /* we take each property names, and collect the ones pointing to null value */
        return Arrays.stream(propertyDescriptors).map(FeatureDescriptor::getName).filter(name -> beanSource.getPropertyValue(name) == null)
                .filter(name -> Arrays.stream(authorizedNullProperties).noneMatch(n -> name.equals(n)))
                .toArray(String[]::new);
    }
}
