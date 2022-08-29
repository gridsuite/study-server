/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.dto;

import java.util.Objects;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.elasticsearch.annotations.Document;

/**
 * @author Hugo Marcellin <hugo.marcelin at rte-france.com>
 */
@SuperBuilder
@NoArgsConstructor
@Getter
@Schema(description = "Case infos")
@Document(indexName = "#{@environment.getProperty('powsybl-ws.elasticsearch.index.prefix')}cases")
@TypeAlias(value = "CaseInfos")
public class CaseInfos {

    public static final String NAME_HEADER_KEY = "name";
    public static final String UUID_HEADER_KEY = "uuid";
    public static final String FORMAT_HEADER_KEY = "format";

    @Id
    @NonNull
    protected UUID uuid;
    @NonNull
    protected String name;
    @NonNull
    protected String format;

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (obj == null) {
            return false;
        }

        if (getClass() != obj.getClass()) {
            return false;
        }

        CaseInfos other = (CaseInfos) obj;
        return Objects.equals(this.uuid, other.uuid) &&
                Objects.equals(this.name, other.name) &&
                Objects.equals(this.format, other.format);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uuid, name, format);
    }
}
