package org.gridsuite.study.server.repository;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.cassandra.core.cql.PrimaryKeyType;
import org.springframework.data.cassandra.core.mapping.PrimaryKeyColumn;
import org.springframework.data.cassandra.core.mapping.Table;

import java.io.Serializable;

/**
 * @author Abdelsalem Hedhili <abdelsalem.hedhili at rte-france.com>
 */

@Getter
@Setter
@AllArgsConstructor
@Table
public class StudyByAccessRights implements Serializable {

    @PrimaryKeyColumn(name = "isPrivate", type = PrimaryKeyType.PARTITIONED)
    private boolean isPrivate;

    @PrimaryKeyColumn(name = "studyName", type = PrimaryKeyType.CLUSTERED)
    private String studyName;

}

