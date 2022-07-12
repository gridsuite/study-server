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
import lombok.Setter;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "networkBuildStep", indexes = {
    @Index(name = "networkBuildBuildNode_idx", columnList = "build_node_id"),
})
public class NetworkBuildEntity {

    @Id
    @GeneratedValue(strategy  =  GenerationType.AUTO)
    @Column
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "build_node_id", foreignKey = @ForeignKey(name = "build_node_fk_constraint"))
    private NodeEntity buildNodeId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "definition_node_id", foreignKey = @ForeignKey(name = "def_node_fk_constraint"))
    private NodeEntity definitionNodeId;

    @Column
    private UUID reportId;
}
