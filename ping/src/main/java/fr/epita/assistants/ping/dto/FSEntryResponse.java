package fr.epita.assistants.ping.dto;

public class FSEntryResponse {
    public String name;
    public String path;
    public boolean isDirectory;

    public FSEntryResponse() {}

    public FSEntryResponse(String name, String path, boolean isDirectory) {
        this.name = name;
        this.path = path;
        this.isDirectory = isDirectory;
    }
}