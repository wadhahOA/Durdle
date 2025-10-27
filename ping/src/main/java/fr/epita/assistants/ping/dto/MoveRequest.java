package fr.epita.assistants.ping.dto;

public class MoveRequest {
    public String src;
    public String dst;

    public MoveRequest() {}

    public MoveRequest(String src, String dst) {
        this.src = src;
        this.dst = dst;
    }
}