/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.dto;

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

/**
 * Pre-formatted DTO for {@code "modules section"} webapp fronts' About dialog
 * @param type the {@link ModuleType type} of module
 * @param name the name of the module
 * @param version the "main"/default version, if need only one version
 * @param gitTag the git tag if in Git versionning repo ; if multiple tags, keep the last
 */
public record AboutInfo(
    ModuleType type,
    String name,
    String version,
    String gitTag
) {

    public enum ModuleType {
        APPS, SERVER, @JsonEnumDefaultValue OTHER;

        @JsonValue
        public String toJson() {
            return this.name().toLowerCase(Locale.ROOT);
        }
    }
}
