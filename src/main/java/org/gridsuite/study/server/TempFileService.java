package org.gridsuite.study.server;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;

@Service
public class TempFileService {
    public Path createTempFile(MultipartFile mpFile, String fileName) throws IOException {
        FileAttribute<Set<PosixFilePermission>> attr = PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------"));
        Path tempFile = Files.createTempFile("tmp_", fileName, attr);
        mpFile.transferTo(tempFile);
        return tempFile;
    }
}
