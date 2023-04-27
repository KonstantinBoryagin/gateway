package ru.example.gateway.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import ru.example.gateway.config.exception.ValidationException;
import ru.example.gateway.model.ErrorResponse;
import ru.example.gateway.model.ModifiedPath;

import java.util.List;
import java.util.Map;

/**
 * Интерфейс для выполнения валидации
 */
public interface IValidationService {
    ObjectMapper mapper = new ObjectMapper();


    /**
     * Выполняет валидацию тела запроса/ответа на основе json схемы заданной в файле конфигурации для его метода и пути
     * @param httpMethod http метод
     * @param body тело запроса
     * @param path путь запроса
     */
    String validate(String httpMethod, String body, String path);

    /**
     * Формируем ответ для пользователя на основе информации из {@link ValidationException} в случае неудачной валидации
     * или других критических ошибках
     */
    default DataBuffer errorHandling(Throwable error, ServerWebExchange exchange) throws JsonProcessingException {
        String status = ((ValidationException) error).getStatus();
        List<String> details = ((ValidationException) error).getDetails();
        HttpStatus httpStatus = ((ValidationException) error).getHttpStatus();
        ErrorResponse message = new ErrorResponse(status, details);
        byte[] bytes = mapper.writeValueAsBytes(message);
        ServerHttpResponse serverHttpResponse = exchange.getResponse();
        serverHttpResponse.setStatusCode(httpStatus);
        serverHttpResponse.getHeaders().add("Content-Type", "application/json");
        return exchange.getResponse().bufferFactory().wrap(bytes);
    }

    /**
     * Проверяет, присутствует ли схема валидации для текущего пути. Так же если путь заканчивается на /{id}
     * и это целое число, то оно отбрасывается при сравнении и записывается в {@link ModifiedPath#setModifiedPath(String)}
     * для дальнейшего использования
     * @param schemaMap Map с путями и схемами
     * @param path путь запроса
     * @return {@link ModifiedPath}
     */
    default ModifiedPath isSchemaMapContainsPath(Map<String, JsonSchema> schemaMap, String path) {
        if(!schemaMap.containsKey(path)) {
            String[] pathArray = new StringBuilder(path).reverse().toString().split("/", 2);
            if(pathArray[0].matches("\\d+")){
                String pathWithoutId = new StringBuilder(pathArray[1]).reverse().toString();
                return ModifiedPath.builder()
                        .isSchemaMapContainsPath(schemaMap.containsKey(pathWithoutId))
                        .isPathModified(schemaMap.containsKey(pathWithoutId))
                        .modifiedPath(pathWithoutId)
                        .build();
            } else {
                return ModifiedPath.builder().isSchemaMapContainsPath(false).build();
            }
        }
        return ModifiedPath.builder().isSchemaMapContainsPath(true).isPathModified(false).build();
    }
}
