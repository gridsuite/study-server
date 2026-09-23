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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
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
        exportContingencyListsAndFilters(parametersDir, filterUuids, contingencyListUuids);
        Path zipFile = createTempExportFile(studyUuid);
        try (OutputStream fos = Files.newOutputStream(zipFile);
             ZipOutputStream zipOut = new ZipOutputStream(fos)) {
            writeZipEntries(tempDir, zipOut);
        }
        return zipFile;
    }

    private void exportContingencyListsAndFilters(Path parametersDir, Set<UUID> filterUuids, Set<UUID> contingencyListUuids) throws IOException {
        // referenced filters are resolved by their owner servers
        Set<UUID> allFilterUuids = new HashSet<>(filterUuids);
        if (!contingencyListUuids.isEmpty()) {
            allFilterUuids.addAll(actionsService.getReferencedFilterUuids(contingencyListUuids));
        }
        if (!allFilterUuids.isEmpty()) {
            allFilterUuids.addAll(filterService.getReferencedFilterUuids(allFilterUuids));
        }
        Map<UUID, JsonNode> contingencyLists = fetchContingencyListsAndFilters(contingencyListUuids, actionsService::getContingencyLists);
        Map<UUID, JsonNode> filters = fetchContingencyListsAndFilters(allFilterUuids, filterService::getFilters);

        Set<UUID> contingencyListsAndFiltersUuids = new HashSet<>(filters.keySet());
        contingencyListsAndFiltersUuids.addAll(contingencyLists.keySet());
        Map<UUID, String> names = directoryService.getElementNames(contingencyListsAndFiltersUuids);

        writeJsonToFileDir(parametersDir.resolve("filters.json"), filters, names);
        writeJsonToFileDir(parametersDir.resolve("contingencyList.json"), contingencyLists, names);
    }

    private Map<UUID, JsonNode> fetchContingencyListsAndFilters(Set<UUID> uuids, Function<Collection<UUID>, String> fetcher) throws IOException {
        Map<UUID, JsonNode> result = new LinkedHashMap<>();
        if (uuids.isEmpty()) {
            return result;
        }
        for (JsonNode contentNode : objectMapper.readTree(fetcher.apply(uuids))) {
            result.put(UUID.fromString(contentNode.get("id").asText()), contentNode);
        }
        Set<UUID> missingUuids = new HashSet<>(uuids);
        missingUuids.removeAll(result.keySet());
        if (!missingUuids.isEmpty()) {
            LOGGER.warn("Elements not found during study export, they will be missing from the archive: {}", missingUuids);
        }
        return result;
    }

    private void writeJsonToFileDir(Path file, Map<UUID, JsonNode> contents, Map<UUID, String> names) throws IOException {
        List<Map<String, Object>> result = new ArrayList<>();
        contents.forEach((uuid, content) -> {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("uuid", uuid);
            entry.put("name", names.get(uuid));
            entry.put("content", content);
            result.add(entry);
        });
        objectMapper.writeValue(file.toFile(), result);
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
