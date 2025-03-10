/*
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.networkmodificationtree.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.gridsuite.study.server.networkmodificationtree.dto.NodeAlias;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

/**
 * @author Hugo Marcellin <hugo.marcelin at rte-france.com>
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Embeddable
@Builder
public class NodeAliasEmbeddable {

    @Column(name = "alias")
    String alias;

    //FetchType set to EAGER because we always retrieve all referencedNode, it avoids N+1 query issue when retrieving nodes name
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "referenced_node", foreignKey = @ForeignKey(name = "node_aliases_node_id_node_fk_2"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    NodeEntity referencedNode;

    public NodeAlias toNodeAlias(String name) {
        return new NodeAlias(referencedNode.getIdNode(), alias, name);
    }
}
