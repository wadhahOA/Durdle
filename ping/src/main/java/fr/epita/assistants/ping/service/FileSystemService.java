package fr.epita.assistants.ping.service;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import fr.epita.assistants.ping.dto.FSEntryResponse;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class FileSystemService {

    public Path resolveSafePath(UUID projectId, String relativePath, String projectPath) throws IOException {
        Path base = Paths.get(projectPath).normalize();
        
        if (relativePath == null || relativePath.trim().isEmpty()) {
            return base;
        }
        
        if (relativePath.startsWith("/")) {
            relativePath = relativePath.substring(1);
        }
        
        if (relativePath.contains("..") || relativePath.startsWith("~")) {
            throw new SecurityException("Path traversal attack");
        }
        
        Path resolved = base.resolve(relativePath).normalize();

        if (!resolved.equals(base) && !resolved.startsWith(base.toString() + File.separator)) {
            throw new SecurityException("Path traversal attack");
        }

        return resolved;
    }

    public List<FSEntryResponse> listFolder(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            throw new NoSuchFileException("Directory does not exist");
        }
        
        if (!Files.isDirectory(dir)) {
            throw new IOException("Path is not a directory");
        }

        try (var stream = Files.list(dir)) {
            return stream.map(path -> {
                String name = path.getFileName().toString();
                String relativePath = name;
                boolean isDirectory = Files.isDirectory(path);
                return new FSEntryResponse(name, relativePath, isDirectory);
            }).collect(Collectors.toList());
        }
    }

    public byte[] readFileBytes(Path file) throws IOException {
        if (!Files.exists(file)) {
            throw new NoSuchFileException("File not found");
        }
        
        if (Files.isDirectory(file)) {
            throw new IOException("Path is a directory, not a file");
        }
        
        return Files.readAllBytes(file);
    }

    public String readFile(Path file) throws IOException {
        if (!Files.exists(file)) {
            throw new NoSuchFileException("File not found");
        }
        
        if (Files.isDirectory(file)) {
            throw new IOException("Path is a directory, not a file");
        }
        
        return Files.readString(file);
    }

    public void createFile(Path file) throws IOException {
        if (Files.exists(file)) {
            throw new FileAlreadyExistsException("File already exists");
        }
        
        Files.createDirectories(file.getParent());
        Files.createFile(file);
    }

    public void uploadFile(Path file, InputStream inputStream) throws IOException {
        Files.createDirectories(file.getParent());
        
        try (var outputStream = Files.newOutputStream(file, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            inputStream.transferTo(outputStream);
        }
    }

    public void writeFile(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    public void deleteFile(Path file) throws IOException {
        if (!Files.exists(file)) {
            throw new NoSuchFileException("File not found");
        }
        
        if (Files.isDirectory(file)) {
            throw new IOException("Path is a directory, use deleteFolder instead");
        }
        
        Files.delete(file);
    }

    public void moveFile(Path src, Path dst) throws IOException {
        if (!Files.exists(src)) {
            throw new NoSuchFileException("Source file not found");
        }
        
        if (Files.isDirectory(src)) {
            throw new IOException("Source is a directory, use moveFolder instead");
        }
        
        if (Files.exists(dst)) {
            throw new FileAlreadyExistsException("Destination file already exists");
        }
        
        Files.createDirectories(dst.getParent());
        Files.move(src, dst);
    }

    public void createFolder(Path dir) throws IOException {
        if (Files.exists(dir)) {
            throw new FileAlreadyExistsException("Folder already exists");
        }
        Files.createDirectories(dir);
    }

    public void deleteFolder(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            throw new NoSuchFileException("Folder not found");
        }
        
        if (!Files.isDirectory(dir)) {
            throw new IOException("Path is not a directory");
        }
        
        try (var walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.delete(path);
                } catch (IOException ignored) {}
            });
        }
    }

    public void moveFolder(Path src, Path dst) throws IOException {
        if (!Files.exists(src)) {
            throw new NoSuchFileException("Source folder not found");
        }
        
        if (!Files.isDirectory(src)) {
            throw new IOException("Source is not a directory");
        }
        
        if (Files.exists(dst)) {
            throw new FileAlreadyExistsException("Destination folder already exists");
        }
        
        Files.createDirectories(dst.getParent());
        Files.move(src, dst);
    }

    public void deletePath(Path path) throws IOException {
        if (!Files.exists(path)) {
            throw new NoSuchFileException("Path not found");
        }

        if (Files.isDirectory(path)) {
            deleteFolder(path);
        } else {
            deleteFile(path);
        }
    }
}