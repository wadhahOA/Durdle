package fr.epita.assistants.ping.errors;

import fr.epita.assistants.ping.utils.HttpError;
import fr.epita.assistants.ping.utils.IHttpError;
import jakarta.ws.rs.core.Response.Status;
import lombok.Getter;

import static jakarta.ws.rs.core.Response.Status.*;


@Getter
public enum ErrorsCode implements IHttpError {
    EXAMPLE_ERROR(BAD_REQUEST, "Example error: %s"),
    ;

    private final HttpError error;

    ErrorsCode(Status status, String message) {
        error = new HttpError(status, message);
    }

    @Override
    public RuntimeException get(Object... args) {
        return error.get(args);
    }

    @Override
    public void throwException(Object... args) {
        throw error.get(args);
    }
}
