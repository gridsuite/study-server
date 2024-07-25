package org.gridsuite.study.server.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.gridsuite.study.server.StudyConstants;

import java.util.List;
import java.util.UUID;

/**
 * @author David Braquart <david.braquart at rte-france.com>
 */
@Schema(description = "Report data")
public record Report(
        UUID id,
        UUID parentId,
        String message,
        StudyConstants.Severity severity,
        List<StudyConstants.Severity> subReportsSeverities,
        List<Report> subReports
) { }
