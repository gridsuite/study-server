package org.gridsuite.study.server.networkmodificationtree.dto;

import org.gridsuite.study.server.dto.networkexport.ExportNetworkStatus;

import java.util.UUID;

public record NodeExportInfos(UUID exportUuid,
                              ExportNetworkStatus status,
                              boolean exportToExplorer,
                              UUID directoryUuid,
                              String filename,
                              String description) {
}
