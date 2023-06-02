package org.gridsuite.study.server.repository;

import lombok.*;

import javax.persistence.*;
import java.util.List;
import java.util.UUID;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Entity
@Builder
@Table(name = "voltageInitParametersVoltageLimits")
public class VoltageInitParametersVoltageLimitsEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "lowVoltageLimit")
    private double lowVoltageLimit;

    @Column(name = "highVoltageLimit")
    private double highVoltageLimit;

    @Column(name = "priority")
    private int priority;

    @ElementCollection
    @CollectionTable(
            name = "voltageInitParametersVoltageLimitsEntityFilters",
            joinColumns = @JoinColumn(name = "voltageLimitId", foreignKey = @ForeignKey(name = "voltageInitVoltageLimitsEntity_filters_fk"))
    )
    private List<FilterEquipmentsEmbeddable> filters;
}
