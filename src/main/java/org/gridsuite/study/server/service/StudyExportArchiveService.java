/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.gridsuite.study.server.dto.RootNetworkInfos;
import org.gridsuite.study.server.dto.studyexport.StudyExportInfos;
import org.gridsuite.study.server.error.StudyException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
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

import static org.gridsuite.study.server.StudyConstants.CASE_API_VERSION;
import static org.gridsuite.study.server.StudyConstants.DELIMITER;
import static org.gridsuite.study.server.error.StudyBusinessErrorCode.EXPORT_STUDY_ERROR;

/**
 * @author Ghazwa Rehili <ghazwa.rehili at rte-france.com>
 */
@Service
public class StudyExportArchiveService {

    private final StudyService studyService;
    private final RootNetworkService rootNetworkService;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${powsybl.services.case-server.base-uri:http://case-server/}")
    private String caseServerBaseUri;

    public StudyExportArchiveService(StudyService studyService, RootNetworkService rootNetworkService, RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.studyService = studyService;
        this.rootNetworkService = rootNetworkService;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Export a study as a zip archive
     * @param studyUuid the study UUID
     * @return InputStreamResource containing the zip archive
     */
    @Transactional(readOnly = true)
    public InputStreamResource exportStudyArchive(UUID studyUuid) {
        try {
            FileAttribute<Set<PosixFilePermission>> attr = PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------"));
            Path tempDir = Files.createTempDirectory("study-export-" + studyUuid, attr);
            Path casesDir = tempDir.resolve("cases");
            Files.createDirectories(casesDir);

            try {
                StudyExportInfos studyExportInfos = studyService.exportStudy(studyUuid);
                List<RootNetworkInfos> rootNetworkInfosList = rootNetworkService.getRootNetworkInfosWithLinksInfos(studyUuid);
                for (RootNetworkInfos rootNetworkInfos : rootNetworkInfosList) {
                    UUID caseUuid = rootNetworkInfos.getCaseInfos().getCaseUuid();
                    String caseName = rootNetworkInfos.getCaseInfos().getCaseName();
                    exportCaseFile(caseUuid, caseName, casesDir);
                }
                Path studyJsonPath = tempDir.resolve("study.json");
                objectMapper.writerWithDefaultPrettyPrinter().writeValue(studyJsonPath.toFile(), studyExportInfos);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                try (ZipOutputStream zipOut = new ZipOutputStream(baos)) {
                    writeZipEntries(tempDir, zipOut);
                    zipOut.finish();
                }
                ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
                return new InputStreamResource(bais);

            } finally {
                deleteDirectory(tempDir);
            }

        } catch (Exception e) {
            throw new StudyException(EXPORT_STUDY_ERROR, "Failed to export study: " + e.getMessage());
        }
    }

    /**
     * Export a case file from case-server
     */
    private void exportCaseFile(UUID caseUuid, String caseName, Path casesDir) throws IOException {
        String path = UriComponentsBuilder.fromPath(DELIMITER + CASE_API_VERSION + "/cases/{caseUuid}").buildAndExpand(caseUuid).toUriString();
        ResponseEntity<byte[]> response = restTemplate.exchange(caseServerBaseUri + path, HttpMethod.GET, null, byte[].class);
        byte[] body = response.getBody();
        if (body != null) {
            Path caseDir = casesDir.resolve(caseUuid.toString());
            Files.createDirectories(caseDir);
            if (isGzipCompressed(body)) {
                body = decompressGzip(body);
            }
            Path caseFile = caseDir.resolve(caseName);
            Files.write(caseFile, body);
        }
    }

    private static boolean isGzipCompressed(byte[] data) {
        return data.length > 2 && data[0] == (byte) 0x1F && data[1] == (byte) 0x8B;
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
