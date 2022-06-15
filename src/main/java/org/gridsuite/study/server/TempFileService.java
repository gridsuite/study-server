package org.gridsuite.study.server;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

import org.springframework.stereotype.Service;

@Service
public class TempFileService {
    public Path createTempFile(String prefix, String suffix, FileAttribute<Set<PosixFilePermission>> attr) throws IOException {
        return Files.createTempFile(prefix, suffix, attr);
    }
}
