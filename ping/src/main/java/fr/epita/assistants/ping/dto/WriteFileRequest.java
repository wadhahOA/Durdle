package fr.epita.assistants.ping.dto;

public class WriteFileRequest {
    public String content;

    public WriteFileRequest() {}

    public WriteFileRequest(String content) {
        this.content = content;
    }
}