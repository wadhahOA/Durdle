package fr.epita.assistants.ping.dto;

public class CreateFolderRequest {
    public String path;

    public CreateFolderRequest() {}

    public CreateFolderRequest(String path) {
        this.path = path;
    }
}