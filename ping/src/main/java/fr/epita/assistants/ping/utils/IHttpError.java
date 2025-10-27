package fr.epita.assistants.ping.utils;

public interface IHttpError {
    RuntimeException get(Object... args);

    void throwException(Object... args);
}
