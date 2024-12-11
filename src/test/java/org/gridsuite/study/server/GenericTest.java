/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server;

import lombok.NonNull;
import org.assertj.core.api.WithAssertions;
import org.gridsuite.study.server.exception.PartialResultException;
import org.gridsuite.study.server.service.client.util.UrlUtil;
import org.gridsuite.study.server.utils.JsonUtils;
import org.gridsuite.study.server.utils.PropertyUtils;
import org.gridsuite.study.server.utils.StudyTestUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.InaccessibleObjectException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;

class GenericTest implements WithAssertions {
    @Test
    void partialResultExceptionConstructor() {
        final String serializableObj = "test";
        final Throwable cause = new Throwable();
        final Throwable causeSuppress = new Throwable(cause);
        causeSuppress.addSuppressed(cause);
        assertThat(new PartialResultException(serializableObj))
                .hasMessage(null).hasNoCause().hasNoSuppressedExceptions().hasFieldOrPropertyWithValue("result", serializableObj);
        assertThat(new PartialResultException(serializableObj, "msg"))
                .hasMessage("msg").hasNoCause().hasNoSuppressedExceptions().hasFieldOrPropertyWithValue("result", serializableObj);
        assertThat(new PartialResultException(serializableObj, "msg", cause))
                .hasMessage("msg").hasCause(cause).hasNoSuppressedExceptions().hasFieldOrPropertyWithValue("result", serializableObj);
        assertThat(new PartialResultException(serializableObj, cause))
                .hasMessage(cause.toString()).hasCause(cause).hasNoSuppressedExceptions().hasFieldOrPropertyWithValue("result", serializableObj);
        assertThat(new PartialResultException(serializableObj, "msg", causeSuppress, false, true))
                .hasMessage("msg").hasCause(causeSuppress).hasNoSuppressedExceptions().hasFieldOrPropertyWithValue("result", serializableObj);
    }

    @ParameterizedTest
    @ValueSource(classes = {JsonUtils.class, UrlUtil.class, PropertyUtils.class, StudyTestUtils.class})
    void utilityClassConstructor(@NonNull final Class<?> utilsClass) {
        assertThat(utilsClass).hasSuperclass(Object.class).isNotInterface().isNotAnnotation().isFinal().satisfiesAnyOf(
                clazz -> assertThat(clazz).isPublic(),
                clazz -> assertThat(clazz).isPackagePrivate()
        );
        assertThat(utilsClass.getPackageName()).startsWith(StudyApplication.class.getPackageName());
        assertThat(utilsClass.getDeclaredConstructors()).singleElement().satisfies(
                constructor -> assertThat(constructor.getParameterCount()).as("constructor parameters").isZero(),
                constructor -> assertThat(Modifier.isPrivate(constructor.getModifiers())).as("constructor is private").isTrue(),
                constructor -> assertThatThrownBy(() -> {
                    constructor.setAccessible(true);
                    constructor.newInstance();
                }).as("constructor thrown exception").isNotNull().satisfiesAnyOf(
                    ex -> assertThat(ex).isNotInstanceOfAny(InaccessibleObjectException.class, SecurityException.class, IllegalAccessException.class,
                            IllegalArgumentException.class, InstantiationException .class, InvocationTargetException.class, ExceptionInInitializerError.class)
                        .message().as("constructor exception message").isNotEmpty(),
                    ex -> assertThat(ex).isInstanceOf(InvocationTargetException.class).hasCause(new UnsupportedOperationException("This is a utility class and cannot be instantiated"))
                )
        );
        assertThat(utilsClass.getDeclaredFields()).allSatisfy(field ->
                assertThat(Modifier.isStatic(field.getModifiers())).as("{} field \"{}\" is static", utilsClass.getName(), field.getName()).isTrue());
    }
}
