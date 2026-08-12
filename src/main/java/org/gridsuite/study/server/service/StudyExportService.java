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
import org.gridsuite.study.server.dto.networkexport.PermissionType;
import org.gridsuite.study.server.dto.studyexport.RootNetworkExportInfos;
import org.gridsuite.study.server.dto.studyexport.TreeExportInfos;
import org.gridsuite.study.server.error.StudyException;
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
import java.util.Comparator;
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
    public static final String TREE_JSON = "tree.json";
    public static final String NETWORK_MODIFICATIONS_JSON = "network-modification.json";
    public static final String CASES = "cases";

    private final StudyService studyService;
    private final CaseService caseService;
    private final DirectoryService directoryService;
    private final ObjectMapper objectMapper;

    public StudyExportService(StudyService studyService, CaseService caseService, DirectoryService directoryService, ObjectMapper objectMapper) {
        this.studyService = studyService;
        this.caseService = caseService;
        this.directoryService = directoryService;
        this.objectMapper = objectMapper;
    }

    /**
     * Export a study as a zip
     * @param studyUuid the study UUID
     * @param userId the requesting user, checked for read access to the study
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
    private Path compressStudyToZip(UUID studyUuid, Path tempDir) throws IOException {
        TreeExportInfos treeExportInfos = studyService.buildTreeExport(studyUuid);
        Path studyJsonPath = tempDir.resolve(TREE_JSON);
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(studyJsonPath.toFile(), treeExportInfos);
        Map<String, String> modificationsByGroup = studyService.buildNetworkModificationsExport(treeExportInfos.nodeTree());
        writeNetworkModificationsExport(modificationsByGroup, tempDir);
        Path casesDir = Files.createDirectories(tempDir.resolve(CASES));
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

    /**
     * Write network-modification.json: for each modification group uuid found in the tree, the list of
     * corresponding modifications, as returned by the network-modification-server export endpoint
     */
    private void writeNetworkModificationsExport(Map<String, String> modificationsByGroup, Path tempDir) throws IOException {
        ObjectNode root = objectMapper.createObjectNode();
        for (Map.Entry<String, String> entry : modificationsByGroup.entrySet()) {
            JsonNode modifications = entry.getValue() != null ? objectMapper.readTree(entry.getValue()) : objectMapper.createArrayNode();
            root.set(entry.getKey(), modifications);
        }
        Path networkModificationsJsonPath = tempDir.resolve(NETWORK_MODIFICATIONS_JSON);
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(networkModificationsJsonPath.toFile(), root);
    }

    private Path createTempWorkDir(UUID studyUuid) {
        FileAttribute<Set<PosixFilePermission>> attr =
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------"));
        return createTempPath(studyUuid, "temp directory", () -> Files.createTempDirectory("study-export-" + studyUuid, attr));
    }

    private Path createTempExportFile(UUID studyUuid) {
        FileAttribute<Set<PosixFilePermission>> attr =
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------"));
        return createTempPath(studyUuid, "temp file", () -> Files.createTempFile("study-export-" + studyUuid, ".zip", attr));
    }

    private Path createTempPath(UUID studyUuid, String errorContext, IOSupplier<Path> creator) {
        try {
            return creator.get();
        } catch (IOException _) {
            throw new StudyException(EXPORT_STUDY_ERROR, "Failed to create " + errorContext + " for study: " + studyUuid);
        }
    }

    @FunctionalInterface
    private interface IOSupplier<T> {
        T get() throws IOException;
    }

    /**
     * Export a case file from case-server
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

    /**
     * Write directory contents to zip archive
     */
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

    /**
     * Recursively delete a directory
     */
    private void deleteDirectory(Path directory) throws IOException {
        if (Files.exists(directory)) {
            walkAndConsume(directory, Comparator.reverseOrder(), Files::delete);
        }
    }

    /**
     * Walk a directory tree and apply action to every path, translating any IOException thrown by action
     * back into a checked IOException (Stream#forEach can't propagate checked exceptions on its own)
     */
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
