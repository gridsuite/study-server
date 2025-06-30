package org.gridsuite.study.server.repository.studylayout;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.util.UUID;

@EqualsAndHashCode
@AllArgsConstructor
public class StudyLayoutKey implements Serializable {
    private UUID studyUuid;
    private String userId;
}
