/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.repository.rootnetwork;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.gridsuite.study.server.dto.BasicRootNetworkInfos;
import org.gridsuite.study.server.dto.RootNetworkCreationRequestInfos;

import java.util.UUID;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "rootNetworkCreationRequest")
public class RootNetworkCreationRequestEntity {
    @Id
    private UUID id;

    private String name;

    private UUID studyUuid;

    private String userId;

    public RootNetworkCreationRequestInfos toDto() {
        return RootNetworkCreationRequestInfos.builder()
            .id(this.getId())
            .studyUuid(this.getStudyUuid())
            .userId(this.getUserId())
            .build();
    }

    public BasicRootNetworkInfos toBasicDto() {
        return new BasicRootNetworkInfos(getId(), getName(), true);
    }
}
