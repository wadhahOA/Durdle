package fr.epita.assistants.ping.dto;

public class FileInfo {
    public String name;
    public boolean isDirectory;
    public long size;

    public FileInfo(String name, boolean isDirectory, long size) {
        this.name = name;
        this.isDirectory = isDirectory;
        this.size = size;
    }
}