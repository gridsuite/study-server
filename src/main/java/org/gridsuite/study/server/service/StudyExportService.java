/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.commons.collections4.CollectionUtils;
import org.gridsuite.filter.AbstractFilter;
import org.gridsuite.study.server.dto.LoadFlowParametersInfos;
import org.gridsuite.study.server.dto.networkexport.PermissionType;
import org.gridsuite.study.server.dto.studyexport.NetworkModificationExportInfos;
import org.gridsuite.study.server.dto.studyexport.RootNetworkExportInfos;
import org.gridsuite.study.server.dto.studyexport.TreeExportInfos;
import org.gridsuite.study.server.error.StudyException;
import org.gridsuite.study.server.service.loadflow.LoadFlowService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.InputStreamResource;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.gridsuite.study.server.error.StudyBusinessErrorCode.EXPORT_STUDY_ERROR;

/**
 * @author Ghazwa Rehili <ghazwa.rehili at rte-france.com>
 */
@Service
public class StudyExportService {
    private static final Logger LOGGER = LoggerFactory.getLogger(StudyExportService.class);
    public static final String TREE_JSON_FILE_NAME = "tree.json";
    public static final String NETWORK_MODIFICATIONS_JSON = "network-modification.json";
    public static final String NETWORK_MODIFICATION_FILTERS_JSON = "network-modification-filters.json";
    public static final String NETWORK_MODIFICATION_LOAD_FLOW_PARAMETERS_JSON = "network-modification-load-flow-parameters.json";
    public static final String CASES_FOLDER = "cases";

    private final StudyService studyService;
    private final CaseService caseService;
    private final DirectoryService directoryService;
    private final LoadFlowService loadFlowService;
    private final ObjectMapper objectMapper;

    public StudyExportService(StudyService studyService, CaseService caseService, DirectoryService directoryService,
                              LoadFlowService loadFlowService, ObjectMapper objectMapper) {
        this.studyService = studyService;
        this.caseService = caseService;
        this.directoryService = directoryService;
        this.loadFlowService = loadFlowService;
        this.objectMapper = objectMapper;
    }

    /**
     * Export a study as a zip
     * @param studyUuid the study UUID
     * @param userId the requesting user checked for read access to the study
     * @return InputStreamResource containing the zip archive
     */
    public InputStreamResource exportStudy(UUID studyUuid, String userId) {
        directoryService.checkPermission(List.of(studyUuid), null, userId, PermissionType.READ, false);
        Path tempDir = createTempWorkDir(studyUuid);
        Path zipFile = null;
        try {
            zipFile = compressStudyToZip(studyUuid, tempDir);
            InputStream stream = Files.newInputStream(zipFile, StandardOpenOption.DELETE_ON_CLOSE);
            zipFile = null;
            return new InputStreamResource(stream);
        } catch (IOException e) {
            throw new StudyException(EXPORT_STUDY_ERROR, e.getMessage());
        } finally {
            try {
                deleteDirectory(tempDir);
            } catch (IOException e) {
                LOGGER.warn("Failed to clean up temp export directory {} for study {}", tempDir, studyUuid, e);
            }
            if (zipFile != null) {
                try {
                    Files.deleteIfExists(zipFile);
                } catch (IOException e) {
                    LOGGER.warn("Failed to delete temp zip file {} for study {}", zipFile, studyUuid, e);
                }
            }
        }
    }

    /**
     * Build tree.json and the case files under tempDir, then compress them into a temp zip file
     */
    private Path compressStudyToZip(UUID studyUuid, Path tempDir) throws IOException {
        TreeExportInfos treeExportInfos = studyService.buildTreeExport(studyUuid);
        Path studyJsonPath = tempDir.resolve(TREE_JSON_FILE_NAME);
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(studyJsonPath.toFile(), treeExportInfos);
        Map<UUID, NetworkModificationExportInfos> modificationsByGroup = studyService.buildNetworkModificationsExport(treeExportInfos.nodeTree());
        writeNetworkModificationsExport(modificationsByGroup, tempDir);
        writeNetworkModificationFiltersExport(modificationsByGroup, tempDir);
        writeLoadFlowParametersExport(modificationsByGroup, tempDir);
        Path casesDir = Files.createDirectories(tempDir.resolve(CASES_FOLDER));
        for (RootNetworkExportInfos rootNetworkInfos : treeExportInfos.rootNetworks()) {
            UUID caseUuid = rootNetworkInfos.caseInfos().getCaseUuid();
            String caseName = rootNetworkInfos.caseInfos().getCaseName();
            exportCaseFile(caseUuid, caseName, casesDir);
        }
        Path zipFile = createTempExportFile(studyUuid);
        try (OutputStream fos = Files.newOutputStream(zipFile);
             ZipOutputStream zipOut = new ZipOutputStream(fos)) {
            writeZipEntries(tempDir, zipOut);
        }
        return zipFile;
    }

    private void writeNetworkModificationsExport(Map<UUID, NetworkModificationExportInfos> modificationsByGroup, Path tempDir) throws IOException {
        Map<String, Map<String, Collection<JsonNode>>> root = new LinkedHashMap<>();
        for (Map.Entry<UUID, NetworkModificationExportInfos> entry : modificationsByGroup.entrySet()) {
            root.put(entry.getKey().toString(), Map.of("modifications", CollectionUtils.emptyIfNull(entry.getValue().exportedModifications())));
        }
        Path networkModificationsJsonPath = tempDir.resolve(NETWORK_MODIFICATIONS_JSON);
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(networkModificationsJsonPath.toFile(), root);
    }

    private void writeNetworkModificationFiltersExport(Map<UUID, NetworkModificationExportInfos> modificationsByGroup, Path tempDir) throws IOException {
        Map<UUID, AbstractFilter> filtersById = new LinkedHashMap<>();
        for (NetworkModificationExportInfos groupExport : modificationsByGroup.values()) {
            Map<UUID, List<AbstractFilter>> filtersByModification = groupExport.exportedFilters();
            if (filtersByModification == null) {
                continue;
            }
            filtersByModification.values().forEach(filtersForModification ->
                    CollectionUtils.emptyIfNull(filtersForModification).forEach(filter -> filtersById.put(filter.getId(), filter)));
        }
        Path filtersJsonPath = tempDir.resolve(NETWORK_MODIFICATION_FILTERS_JSON);
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(filtersJsonPath.toFile(), filtersById);
    }

    private void writeLoadFlowParametersExport(Map<UUID, NetworkModificationExportInfos> modificationsByGroup, Path tempDir) throws IOException {
        Set<UUID> loadFlowParametersIds = new LinkedHashSet<>();
        for (NetworkModificationExportInfos groupExport : modificationsByGroup.values()) {
            Map<UUID, UUID> loadFlowParametersByModification = groupExport.exportedLoadFlowParameters();
            if (loadFlowParametersByModification != null) {
                loadFlowParametersIds.addAll(loadFlowParametersByModification.values());
            }
        }
        ObjectNode loadFlowParametersRoot = objectMapper.createObjectNode();
        for (UUID loadFlowParametersId : loadFlowParametersIds) {
            LoadFlowParametersInfos loadFlowParametersInfos = loadFlowService.getLoadFlowParameters(loadFlowParametersId);
            if (loadFlowParametersInfos != null) {
                loadFlowParametersRoot.set(loadFlowParametersId.toString(), objectMapper.valueToTree(loadFlowParametersInfos));
            }
        }
        Path loadFlowParametersJsonPath = tempDir.resolve(NETWORK_MODIFICATION_LOAD_FLOW_PARAMETERS_JSON);
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(loadFlowParametersJsonPath.toFile(), loadFlowParametersRoot);
    }

    private Path createTempWorkDir(UUID studyUuid) {
        return createTempPath(studyUuid, "temp directory", "rwx------",
                attr -> Files.createTempDirectory("study-export-" + studyUuid, attr));
    }

    private Path createTempExportFile(UUID studyUuid) {
        return createTempPath(studyUuid, "temp file", "rw-------",
                attr -> Files.createTempFile("study-export-" + studyUuid, ".zip", attr));
    }

    private Path createTempPath(UUID studyUuid, String errorContext, String permissions,
                                IOFunction<FileAttribute<Set<PosixFilePermission>>, Path> creator) {
        FileAttribute<Set<PosixFilePermission>> attr =
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString(permissions));
        try {
            return creator.apply(attr);
        } catch (IOException e) {
            throw new StudyException(EXPORT_STUDY_ERROR, e.getMessage());
        }
    }

    @FunctionalInterface
    private interface IOFunction<T, R> {
        R apply(T t) throws IOException;
    }

    /**
     * Export a case file from the case-server, streaming it directly to disk
     * so the whole case content is never buffered in the heap.
     */
    private void exportCaseFile(UUID caseUuid, String caseName, Path casesDir) throws IOException {
        Path caseDir = casesDir.resolve(caseUuid.toString());
        Files.createDirectories(caseDir);
        Path caseFile = caseDir.resolve(caseName);
        caseService.streamCaseContent(caseUuid, (contentEncoding, body) -> {
            // plain file cases are gzip'd by the case-server and need to be decompressed
            try (InputStream in = "gzip".equalsIgnoreCase(contentEncoding) ? new GZIPInputStream(body) : body;
                 OutputStream out = Files.newOutputStream(caseFile)) {
                in.transferTo(out);
            }
        });
    }

    private void writeZipEntries(Path directory, ZipOutputStream zipOut) throws IOException {
        walkAndConsume(directory, null, file -> {
            if (Files.isRegularFile(file)) {
                Path relativePath = directory.relativize(file);
                String entryName = relativePath.toString().replace('\\', '/');
                ZipEntry entry = new ZipEntry(entryName);
                entry.setSize(Files.size(file));
                zipOut.putNextEntry(entry);
                try (InputStream in = Files.newInputStream(file)) {
                    in.transferTo(zipOut);
                }
                zipOut.closeEntry();
            }
        });
    }

    private void deleteDirectory(Path directory) throws IOException {
        if (Files.exists(directory)) {
            walkAndConsume(directory, Comparator.reverseOrder(), Files::delete);
        }
    }

    private void walkAndConsume(Path directory, Comparator<Path> order, IOConsumer<Path> action) throws IOException {
        try (Stream<Path> paths = Files.walk(directory)) {
            (order == null ? paths : paths.sorted(order)).forEach(path -> {
                try {
                    action.accept(path);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }

    @FunctionalInterface
    private interface IOConsumer<T> {
        void accept(T t) throws IOException;
    }
}
