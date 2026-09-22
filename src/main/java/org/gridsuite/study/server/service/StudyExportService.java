/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.gridsuite.study.server.dto.networkexport.PermissionType;
import org.gridsuite.study.server.dto.studyexport.RootNetworkExportInfos;
import org.gridsuite.study.server.dto.studyexport.TreeExportInfos;
import org.gridsuite.study.server.error.StudyException;
import org.gridsuite.study.server.service.common.ComputationParametersService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
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
    public static final String CASES_FOLDER = "cases";
    public static final String PARAMETERS_FOLDER = "computationParameters";

    private final StudyService studyService;
    private final CaseService caseService;
    private final DirectoryService directoryService;
    private final ObjectMapper objectMapper;
    private final ComputationParametersService computationParametersService;
    private final FilterService filterService;
    private final ActionsService actionsService;

    public StudyExportService(StudyService studyService, CaseService caseService, DirectoryService directoryService,
                              ObjectMapper objectMapper, ComputationParametersService computationParametersService,
                              FilterService filterService, ActionsService actionsService) {
        this.studyService = studyService;
        this.caseService = caseService;
        this.directoryService = directoryService;
        this.objectMapper = objectMapper;
        this.computationParametersService = computationParametersService;
        this.filterService = filterService;
        this.actionsService = actionsService;
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
            zipFile = compressStudyToZip(studyUuid, userId, tempDir);
            InputStream stream = Files.newInputStream(zipFile, StandardOpenOption.DELETE_ON_CLOSE);
            zipFile = null;
            return new InputStreamResource(stream);
        } catch (IOException _) {
            throw new StudyException(EXPORT_STUDY_ERROR, "Failed to export study: " + studyUuid);
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
    private Path compressStudyToZip(UUID studyUuid, String userId, Path tempDir) throws IOException {
        TreeExportInfos treeExportInfos = studyService.buildTreeExport(studyUuid);
        Path studyJsonPath = tempDir.resolve(TREE_JSON_FILE_NAME);
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(studyJsonPath.toFile(), treeExportInfos);
        Path casesDir = Files.createDirectories(tempDir.resolve(CASES_FOLDER));
        for (RootNetworkExportInfos rootNetworkInfos : treeExportInfos.rootNetworks()) {
            UUID caseUuid = rootNetworkInfos.caseInfos().getCaseUuid();
            String caseName = rootNetworkInfos.caseInfos().getCaseName();
            exportCaseFile(caseUuid, caseName, casesDir);
        }
        Path parametersDir = tempDir.resolve(PARAMETERS_FOLDER);
        Set<UUID> filterUuids = new HashSet<>();
        Set<UUID> contingencyListUuids = new HashSet<>();
        computationParametersService.exportParameters(studyService.getStudy(studyUuid), userId, parametersDir, filterUuids, contingencyListUuids);
        exportDefinitions(parametersDir, filterUuids, contingencyListUuids);
        Path zipFile = createTempExportFile(studyUuid);
        try (OutputStream fos = Files.newOutputStream(zipFile);
             ZipOutputStream zipOut = new ZipOutputStream(fos)) {
            writeZipEntries(tempDir, zipOut);
        }
        return zipFile;
    }

    private void exportDefinitions(Path parametersDir, Set<UUID> filterUuids, Set<UUID> contingencyListUuids) throws IOException {
        Map<UUID, JsonNode> contingencyLists = new LinkedHashMap<>();
        for (UUID contingencyListUuid : contingencyListUuids) {
            String content = actionsService.getContingencyList(contingencyListUuid);
            if (content == null) {
                continue;
            }
            JsonNode contentNode = objectMapper.readTree(content);
            contingencyLists.put(contingencyListUuid, contentNode);
            contentNode.path("filters").findValues("id").forEach(id -> filterUuids.add(UUID.fromString(id.asText())));
            contentNode.path("selectedEquipmentTypesByFilter").findValues("filterId").forEach(id -> filterUuids.add(UUID.fromString(id.asText())));
        }

        Map<UUID, JsonNode> filters = new LinkedHashMap<>();
        Deque<UUID> filtersToExport = new ArrayDeque<>(filterUuids);
        while (!filtersToExport.isEmpty()) {
            UUID filterUuid = filtersToExport.poll();
            if (filters.containsKey(filterUuid)) {
                continue;
            }
            String content = filterService.getFilter(filterUuid);
            if (content == null) {
                continue;
            }
            JsonNode contentNode = objectMapper.readTree(content);
            filters.put(filterUuid, contentNode);
            for (JsonNode rule : contentNode.findParents("dataType")) {
                if ("FILTER_UUID".equals(rule.get("dataType").asText())) {
                    rule.path("values").forEach(value -> filtersToExport.add(UUID.fromString(value.asText())));
                }
            }
        }

        Set<UUID> allUuids = new HashSet<>(filters.keySet());
        allUuids.addAll(contingencyLists.keySet());
        Map<UUID, String> names = directoryService.getElementNames(allUuids);

        writeDefinitions(parametersDir.resolve("filterDefinitions.json"), filters, names);
        writeDefinitions(parametersDir.resolve("contingencyListDefinitions.json"), contingencyLists, names);
    }

    private void writeDefinitions(Path file, Map<UUID, JsonNode> contents, Map<UUID, String> names) throws IOException {
        List<Map<String, Object>> definitions = new ArrayList<>();
        contents.forEach((uuid, content) -> definitions.add(Map.of("uuid", uuid, "name", names.getOrDefault(uuid, ""), "content", content)));
        objectMapper.writeValue(file.toFile(), definitions);
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
        } catch (IOException _) {
            throw new StudyException(EXPORT_STUDY_ERROR, "Failed to create " + errorContext + " for study: " + studyUuid);
        }
    }

    @FunctionalInterface
    private interface IOFunction<T, R> {
        R apply(T t) throws IOException;
    }

    /**
     * Export a case file from the case-server
     */
    private void exportCaseFile(UUID caseUuid, String caseName, Path casesDir) throws IOException {
        ResponseEntity<byte[]> response = caseService.getCaseContent(caseUuid);
        byte[] body = response.getBody();
        if (body != null) {
            Path caseDir = casesDir.resolve(caseUuid.toString());
            Files.createDirectories(caseDir);
            String contentEncoding = response.getHeaders().getFirst(HttpHeaders.CONTENT_ENCODING);
            // plain file cases are gzip by the case-server and need to be decompressed
            if ("gzip".equalsIgnoreCase(contentEncoding)) {
                body = decompressGzip(body);
            }
            Path caseFile = caseDir.resolve(caseName);
            Files.write(caseFile, body);
        }
    }

    private static byte[] decompressGzip(byte[] data) throws IOException {
        try (GZIPInputStream gzipIn = new GZIPInputStream(new ByteArrayInputStream(data));
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            gzipIn.transferTo(out);
            return out.toByteArray();
        }
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
