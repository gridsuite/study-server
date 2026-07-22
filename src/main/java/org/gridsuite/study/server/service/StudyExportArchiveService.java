/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
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
import java.util.List;
import java.util.UUID;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.gridsuite.study.server.StudyConstants.CASE_API_VERSION;
import static org.gridsuite.study.server.StudyConstants.DELIMITER;
import static org.gridsuite.study.server.error.StudyBusinessErrorCode.EXPORT_STUDY_ERROR;

/**
 * Service to export a study as a zip archive containing:
 * - study.json: study structure and metadata
 * - cases/{caseUuid}/{caseName}.xiidm: network case files
 *
 * @author Claude Code
 */
@Service
@Slf4j
public class StudyExportArchiveService {

    private final StudyService studyService;
    private final RootNetworkService rootNetworkService;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${powsybl.services.case-server.base-uri:http://case-server/}")
    private String caseServerBaseUri;

    public StudyExportArchiveService(
            StudyService studyService,
            RootNetworkService rootNetworkService,
            RestTemplate restTemplate,
            ObjectMapper objectMapper) {
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
            log.info("Starting export of study {}", studyUuid);

            // Create temporary directory for export
            Path tempDir = Files.createTempDirectory("study-export-" + studyUuid);
            Path casesDir = tempDir.resolve("cases");
            Files.createDirectories(casesDir);

            try {
                // Get study export data
                StudyExportInfos studyExportInfos = studyService.exportStudy(studyUuid);
                List<RootNetworkInfos> rootNetworkInfosList = rootNetworkService.getRootNetworkInfosWithLinksInfos(studyUuid);

                // Export all case files
                for (RootNetworkInfos rootNetworkInfos : rootNetworkInfosList) {
                    UUID caseUuid = rootNetworkInfos.getCaseInfos().getCaseUuid();
                    String caseName = rootNetworkInfos.getCaseInfos().getCaseName();
                    exportCaseFile(caseUuid, caseName, casesDir);
                }

                // Write study.json
                Path studyJsonPath = tempDir.resolve("study.json");
                objectMapper.writerWithDefaultPrettyPrinter()
                        .writeValue(studyJsonPath.toFile(), studyExportInfos);

                log.debug("Study JSON written to {}, size: {} bytes", studyJsonPath, Files.size(studyJsonPath));

                // Create zip archive
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                try (ZipOutputStream zipOut = new ZipOutputStream(baos)) {
                    writeZipEntries(tempDir, zipOut);
                    zipOut.finish();
                }

                log.info("Successfully exported study {} ({} bytes)", studyUuid, baos.size());
                System.out.println("Study JSON written to " + studyJsonPath + ", size: " + Files.size(studyJsonPath) + " bytes");
                System.out.println("studyExportInfos: " + studyExportInfos);
                System.out.println("studyExportInfos JSON: " + objectMapper.writeValueAsString(studyExportInfos));
                ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
                return new InputStreamResource(bais);

            } finally {
                // Cleanup temp directory
                deleteDirectory(tempDir);
            }

        } catch (Exception e) {
            log.error("Error exporting study {}", studyUuid, e);
            throw new StudyException(EXPORT_STUDY_ERROR, "Failed to export study: " + e.getMessage());
        }
    }

    /**
     * Export a case file from case-server
     */
    private void exportCaseFile(UUID caseUuid, String caseName, Path casesDir) throws IOException {
        log.debug("Exporting case {} ({})", caseUuid, caseName);

        String path = UriComponentsBuilder
                .fromPath(DELIMITER + CASE_API_VERSION + "/cases/{caseUuid}")
                .buildAndExpand(caseUuid)
                .toUriString();

        ResponseEntity<byte[]> response = restTemplate.exchange(
                caseServerBaseUri + path,
                HttpMethod.GET,
                null,
                byte[].class
        );
        byte[] body = response.getBody();
        if (body != null) {
            Path caseDir = casesDir.resolve(caseUuid.toString());
            Files.createDirectories(caseDir);

            // case-server stocke les fichiers "plain" compressés en gzip ;
            // ce endpoint renvoie les octets tels que stockés -> il faut décompresser
            // avant de les réécrire, sinon le fichier réimporté est illisible
            if (isGzipCompressed(body)) {
                body = decompressGzip(body);
            }

            // caseName contient déjà le nom + l'extension d'origine (ex: "LILLE.xiidm", "foo.zip")
            // -> ne rien ajouter, sous peine de dupliquer/corrompre l'extension
            Path caseFile = caseDir.resolve(caseName);
            Files.write(caseFile, body);
            log.debug("Exported case file to {}", caseFile);
        }
    }

    private static boolean isGzipCompressed(byte[] data) {
        return data.length > 2 && data[0] == (byte) 0x1f && data[1] == (byte) 0x8b;
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
        Files.walk(directory)
                .filter(Files::isRegularFile)
                .forEach(file -> {
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
                        log.debug("Added to zip: {} ({} bytes)", entryName, Files.size(file));
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
    }

    /**
     * Recursively delete a directory
     */
    private void deleteDirectory(Path directory) throws IOException {
        if (Files.exists(directory)) {
            Files.walk(directory)
                    .sorted((a, b) -> b.compareTo(a)) // Delete files before directories
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            log.warn("Failed to delete {}", path, e);
                        }
                    });
        }
    }
}
