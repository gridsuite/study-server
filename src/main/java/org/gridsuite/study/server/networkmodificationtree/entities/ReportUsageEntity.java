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
    private NodeEntity buildNodeId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "definition_node_id", foreignKey = @ForeignKey(name = "def_node_fk_constraint"))
    private NodeEntity definitionNodeId;

}
