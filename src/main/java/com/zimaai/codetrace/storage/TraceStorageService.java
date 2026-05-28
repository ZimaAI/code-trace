package com.zimaai.codetrace.storage;

import com.intellij.openapi.project.Project;
import com.zimaai.codetrace.model.TraceDocument;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class TraceStorageService {
    private final TraceProjectPaths paths;
    private final TraceJsonMapper mapper;

    public TraceStorageService(Project project, TraceJsonMapper mapper) {
        this.paths = new TraceProjectPaths(project);
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    public TraceStorageService(Path projectBasePath, TraceJsonMapper mapper) {
        this.paths = new TraceProjectPaths(projectBasePath);
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    public List<String> listFiles() {
        try {
            ensureDirectory();
            try (var stream = Files.list(paths.traceDirectory())) {
                return stream
                        .filter(Files::isRegularFile)
                        .map(path -> path.getFileName().toString())
                        .filter(name -> name.toLowerCase(Locale.ROOT).endsWith(".json"))
                        .sorted()
                        .toList();
            }
        } catch (IOException exception) {
            throw new TraceStorageException("Failed to list trace files", exception);
        }
    }

    public TraceDocument load(String fileName) {
        try {
            String json = Files.readString(resolve(fileName));
            return mapper.read(json);
        } catch (Exception exception) {
            throw new TraceStorageException("Failed to load trace file: " + fileName, exception);
        }
    }

    public void save(String fileName, TraceDocument document) {
        try {
            ensureDirectory();
            Files.writeString(resolve(fileName), mapper.write(document));
        } catch (Exception exception) {
            throw new TraceStorageException("Failed to save trace file: " + fileName, exception);
        }
    }

    public void rename(String oldFileName, String newFileName) {
        try {
            Files.move(resolve(oldFileName), resolve(newFileName), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            throw new TraceStorageException("Failed to rename trace file", exception);
        }
    }

    public void copy(String sourceFileName, String targetFileName) {
        try {
            Files.copy(resolve(sourceFileName), resolve(targetFileName), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            throw new TraceStorageException("Failed to copy trace file", exception);
        }
    }

    public void delete(String fileName) {
        try {
            Files.deleteIfExists(resolve(fileName));
        } catch (IOException exception) {
            throw new TraceStorageException("Failed to delete trace file", exception);
        }
    }

    private void ensureDirectory() throws IOException {
        Files.createDirectories(paths.traceDirectory());
    }

    private Path resolve(String fileName) {
        return paths.traceDirectory().resolve(fileName);
    }
}
