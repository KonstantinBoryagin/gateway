package ru.example.gateway.model;

import lombok.Getter;
import lombok.Setter;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * POJO для включения/отключения валидации запросов и ответов. Считывается из файла свойств по ключу 'validate'
 */
@Component
@ConfigurationProperties(prefix = "validate")
@Setter
@Getter
public class ValidateActivator {

    public boolean requestOn;
    private boolean responseOn;

}
