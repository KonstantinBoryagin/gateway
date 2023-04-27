package ru.example.gateway.model;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * Представление ответа от сервера в случае ошибки валидации
 */
@Data
@AllArgsConstructor
public class ErrorResponse {

    private String status;
    private List<String> details;

}
