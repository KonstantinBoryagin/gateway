package ru.example.gateway.config.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.util.List;

/**
 * Исключение для передачи информации внутри шлюза
 */
public class ValidationException  extends RuntimeException{

    @Getter
    private final String status;

    @Getter
    private final List<String> details;

    @Getter
    private final HttpStatus httpStatus;

    public ValidationException(String status, List<String> details, HttpStatus httpStatus) {
        this.status = status;
        this.details = details;
        this.httpStatus = httpStatus;
    }
}
