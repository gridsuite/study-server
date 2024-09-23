package org.gridsuite.study.server.repository.timepoint;

import jakarta.persistence.*;
import lombok.*;
import org.gridsuite.study.server.networkmodificationtree.entities.TimePointNodeInfoEntity;
import org.gridsuite.study.server.repository.StudyEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
/**
 * @author Le Saulnier Kevin <lesaulnier.kevin at rte-france.com>
 */

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Entity
@Builder
@Table(name = "timepoint")
public class TimePointEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id")
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "studyUuid")
    private StudyEntity study;

    @OneToMany(orphanRemoval = true, mappedBy = "timePoint", cascade = CascadeType.ALL)
    private List<TimePointNodeInfoEntity> timePointNodeInfos;

    @Column(name = "networkUuid", nullable = false)
    private UUID networkUuid;

    @Column(name = "networkId", nullable = false)
    private String networkId;

    @Column(name = "caseFormat", nullable = false)
    private String caseFormat;

    @Column(name = "caseUuid", nullable = false)
    private UUID caseUuid;

    @Column(name = "caseName", nullable = false)
    private String caseName;

    // reportUuid of network import
    @Column(name = "reportUuid")
    private UUID reportUuid;

    public void addTimePointNodeInfo(TimePointNodeInfoEntity timePointNodeInfoEntity) {
        if (timePointNodeInfos == null) {
            timePointNodeInfos = new ArrayList<>();
        }
        timePointNodeInfoEntity.setTimePoint(this);
        timePointNodeInfos.add(timePointNodeInfoEntity);
    }
}