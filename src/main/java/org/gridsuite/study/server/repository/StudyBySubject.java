package org.gridsuite.study.server.repository;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.cassandra.core.cql.PrimaryKeyType;
import org.springframework.data.cassandra.core.mapping.Column;
import org.springframework.data.cassandra.core.mapping.PrimaryKeyColumn;
import org.springframework.data.cassandra.core.mapping.Table;

import java.io.Serializable;
import java.util.UUID;

/**
 * @author Abdelsalem Hedhili <abdelsalem.hedhili at rte-france.com>
 */

@Getter
@Setter
@AllArgsConstructor
@Table
public class StudyBySubject implements Serializable {

    @PrimaryKeyColumn(name = "userId", type = PrimaryKeyType.PARTITIONED)
    private String userId;

    @PrimaryKeyColumn(name = "isPrivate", type = PrimaryKeyType.CLUSTERED)
    private boolean isPrivate;

    @PrimaryKeyColumn(name = "studyName", type = PrimaryKeyType.CLUSTERED)
    private String studyName;

    @Column("networkUuid")
    private UUID networkUuid;

    @Column("networkId")
    private String networkId;

    @Column("description")
    private String description;

    @Column("caseFormat")
    private String caseFormat;

    @Column("caseUuid")
    private UUID caseUuid;

    @Column("casePrivate")
    private boolean casePrivate;

}
