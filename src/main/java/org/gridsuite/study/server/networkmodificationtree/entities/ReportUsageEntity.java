/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.networkmodificationtree.entities;

import java.util.UUID;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.ForeignKey;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Entity
@Table(name = "report_usage", indexes = {
    @Index(name = "ReportUsage_user_idx", columnList = "build_node_id"),
    @Index(name = "ReportUsage_used_idx", columnList = "report_id"),
})
public class ReportUsageEntity {

    @Id
    @GeneratedValue(strategy  =  GenerationType.AUTO)
    @Column(name = "id")
    private UUID id;

    @Column(name = "report_id")
    private UUID reportId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "build_node_id", foreignKey = @ForeignKey(name = "build_node_fk_constraint"))
    private NodeEntity buildNode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "definition_node_id", foreignKey = @ForeignKey(name = "def_node_fk_constraint"))
    private NodeEntity definitionNode;

    public String toString() {
        return "ReportUsageEntity{" +
            "id=" + id +
            ", reportId=" + reportId +
            ", buildNode=" + buildNode.getIdNode() +
            ", definitionNode=" + definitionNode.getIdNode() +
            '}';
    }
}
