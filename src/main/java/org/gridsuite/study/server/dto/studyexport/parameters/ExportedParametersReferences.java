/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.dto.studyexport.parameters;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Ghazwa Rehili <ghazwa.rehili at rte-france.com>
 */
public interface ExportedParametersReferences {

    default Set<UUID> getFilterUuids() {
        return Set.of();
    }

    default Set<UUID> getContingencyListUuids() {
        return Set.of();
    }

    static <T> Stream<T> nullSafe(List<T> list) {
        return list == null ? Stream.empty() : list.stream();
    }

    static Set<UUID> toUuidSet(Stream<UUID> uuids) {
        return uuids.filter(Objects::nonNull).collect(Collectors.toSet());
    }
}
