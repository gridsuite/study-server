/*
 * Copyright 2002-2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gridsuite.study.server;

import lombok.Data;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.convert.converter.ConverterFactory;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Converts from a String to a {@link Enum} by calling {@link Enum#valueOf(Class, String)},
 * while being case-insensitive.
 *
 * @implNote based on existing spring converters code
 *
 * @see org.springframework.core.convert.support.StringToEnumConverterFactory
 * @see org.springframework.boot.convert.LenientStringToEnumConverterFactory
 */
@Component //spring-boot implicit WebMvcConfigurer#addFormatters(registry.addConverter(...))
@SuppressWarnings({"rawtypes", "unchecked"})
public final class InsensitiveStringToEnumConverterFactory implements ConverterFactory<String, Enum> {
    /**
     * {@inheritDoc}
     */
    @Override
    public <T extends Enum> Converter<String, T> getConverter(Class<T> targetType) {
        return new CaseInsensitiveStringToEnum<>((Class<T>) getEnumType(targetType));
    }

    /**
     * @see org.springframework.core.convert.support.ConversionUtils#getEnumType(Class)
     */
    public static Class<Enum<?>> getEnumType(@NonNull final Class<?> targetType) {
        Class<?> enumType = targetType;
        while (enumType != null && !enumType.isEnum()) {
            enumType = enumType.getSuperclass();
        }
        Assert.notNull(enumType, () -> "The target type " + targetType.getName() + " does not refer to an enum");
        return (Class<Enum<?>>) enumType;
    }

    @Data
    public static class CaseInsensitiveStringToEnum<E extends Enum<E>> implements Converter<Object, E> {
        private final Map<String, E> values;

        public CaseInsensitiveStringToEnum(final Class<E> enumType) {
            this.values = EnumSet.allOf(enumType).stream()
                    .collect(Collectors.toMap(e -> e.name().toUpperCase(Locale.ROOT), Function.identity()));
        }

        @Override
        @Nullable
        public E convert(Object source) {
            final String value = source.toString().trim().toUpperCase(Locale.ROOT);
            if (value.isEmpty()) {
                // It's an empty enum identifier: reset the enum value to null.
                return null;
            } else {
                return this.values.get(value);
            }
        }
    }
}
