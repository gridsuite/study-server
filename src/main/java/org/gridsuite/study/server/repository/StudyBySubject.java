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
public class StudyBySubject implements Serializable {

    @PrimaryKeyColumn(name = "subject", type = PrimaryKeyType.PARTITIONED)
    private String subject;

    @PrimaryKeyColumn(name = "studyName", type = PrimaryKeyType.CLUSTERED)
    private String studyName;

}
