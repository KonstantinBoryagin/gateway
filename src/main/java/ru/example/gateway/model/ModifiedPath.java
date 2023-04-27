package ru.example.gateway.model;

import lombok.Builder;
import lombok.Data;

/**
 * POJO для проверки наличия проверки схемы валидации для пути запроса и в случае запроса в формате
 * /{id}(может меняться при каждом запросе) в конце хранит в себе корректный путь к схеме
 */
@Data
@Builder
public class ModifiedPath {

    private boolean isSchemaMapContainsPath;
    private boolean isPathModified;
    private String modifiedPath;
}
