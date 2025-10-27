package fr.epita.assistants.ping.dto;

public class PathRequest {
    public String relativePath;

    public PathRequest() {}

    public PathRequest(String relativePath) {
        this.relativePath = relativePath;
    }
}