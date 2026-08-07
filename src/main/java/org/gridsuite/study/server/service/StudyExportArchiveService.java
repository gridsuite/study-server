/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.gridsuite.study.server.dto.RootNetworkInfos;
import org.gridsuite.study.server.dto.networkexport.PermissionType;
import org.gridsuite.study.server.dto.studyexport.TreeExportInfos;
import org.gridsuite.study.server.error.StudyException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Comparator;
import java.util.List;
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
public class StudyExportArchiveService {
    private static final Logger LOGGER = LoggerFactory.getLogger(StudyExportArchiveService.class);

    private final StudyService studyService;
    private final RootNetworkService rootNetworkService;
    private final CaseService caseService;
    private final DirectoryService directoryService;
    private final ObjectMapper objectMapper;
    private final StudyExportArchiveService self;

    public StudyExportArchiveService(@Lazy StudyExportArchiveService self, StudyService studyService, RootNetworkService rootNetworkService,
                                     CaseService caseService, DirectoryService directoryService, ObjectMapper objectMapper) {
        this.self = self;
        this.studyService = studyService;
        this.rootNetworkService = rootNetworkService;
        this.caseService = caseService;
        this.directoryService = directoryService;
        this.objectMapper = objectMapper;
    }

    /**
     * Export a study as a gzip archive
     * @param studyUuid the study UUID
     * @param userId the requesting user, checked for read access to the study
     * @return InputStreamResource containing the zip archive
     */
    public InputStreamResource exportStudyArchive(UUID studyUuid, String userId) {
        directoryService.checkPermission(List.of(studyUuid), null, userId, PermissionType.READ, false);
        Path tempDir = createTempWorkDir(studyUuid);
        Path zipFile = null;
        try {
            List<RootNetworkInfos> rootNetworkInfosList = self.loadRootNetworkInfosAndWriteTree(studyUuid, tempDir);
            Path casesDir = Files.createDirectories(tempDir.resolve("cases"));
            for (RootNetworkInfos rootNetworkInfos : rootNetworkInfosList) {
                UUID caseUuid = rootNetworkInfos.getCaseInfos().getCaseUuid();
                String caseName = rootNetworkInfos.getCaseInfos().getCaseName();
                exportCaseFile(caseUuid, caseName, casesDir);
            }
            zipFile = createTempExportFile(studyUuid);
            try (OutputStream fos = Files.newOutputStream(zipFile);
                 ZipOutputStream zipOut = new ZipOutputStream(fos)) {
                writeZipEntries(tempDir, zipOut);
            }
            InputStream stream = Files.newInputStream(zipFile, StandardOpenOption.DELETE_ON_CLOSE);
            zipFile = null;
            return new InputStreamResource(stream);
        } catch (IOException e) {
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

    @Transactional(readOnly = true)
    protected List<RootNetworkInfos> loadRootNetworkInfosAndWriteTree(UUID studyUuid, Path tempDir) throws IOException {
        TreeExportInfos treeExportInfos = studyService.exportStudy(studyUuid);
        List<RootNetworkInfos> rootNetworkInfosList = rootNetworkService.getRootNetworkInfosWithLinksInfos(studyUuid);
        Path studyJsonPath = tempDir.resolve("tree.json");
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(studyJsonPath.toFile(), treeExportInfos);
        return rootNetworkInfosList;
    }

    private Path createTempWorkDir(UUID studyUuid) {
        try {
            FileAttribute<Set<PosixFilePermission>> attr =
                    PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------"));
            return Files.createTempDirectory("study-export-" + studyUuid, attr);
        } catch (IOException e) {
            throw new StudyException(EXPORT_STUDY_ERROR, "Failed to create temp directory for study: " + studyUuid);
        }
    }

    private Path createTempExportFile(UUID studyUuid) {
        try {
            FileAttribute<Set<PosixFilePermission>> attr =
                    PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------"));
            return Files.createTempFile("study-export-" + studyUuid, ".zip", attr);
        } catch (IOException e) {
            throw new StudyException(EXPORT_STUDY_ERROR, "Failed to create temp file for study: " + studyUuid);
        }
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
        try (Stream<Path> paths = Files.walk(directory)) {
            paths.filter(Files::isRegularFile).forEach(file -> {
                try {
                    Path relativePath = directory.relativize(file);
                    String entryName = relativePath.toString().replace('\\', '/');
                    ZipEntry entry = new ZipEntry(entryName);
                    entry.setSize(Files.size(file));
                    zipOut.putNextEntry(entry);
                    try (InputStream in = Files.newInputStream(file)) {
                        in.transferTo(zipOut);
                    }
                    zipOut.closeEntry();
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        }
    }

    /**
     * Recursively delete a directory
     */
    private void deleteDirectory(Path directory) throws IOException {
        if (Files.exists(directory)) {
            try (Stream<Path> paths = Files.walk(directory)) {
                paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
            } catch (UncheckedIOException e) {
                throw e.getCause();
            }
        }
    }
}
