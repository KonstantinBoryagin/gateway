package ru.example.gateway.service.implementation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.networknt.schema.JsonSchema;
import com.networknt.schema.ValidationMessage;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

import ru.example.gateway.config.exception.ValidationException;
import ru.example.gateway.model.ModifiedPath;
import ru.example.gateway.service.IValidationService;

@Service
@Slf4j
public class ResponseValidationService implements IValidationService {

    private static final String SERVER_ERROR_MESSAGE = "Error";
    private static final String SERVER_DETAILS_MESSAGE = "Server response error";

    private ObjectMapper mapper;
    @Autowired
    public void setMapper(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Resource(name = "responseSchemaMap")
    private Map<String, Map<String, JsonSchema>> responsesSchema;

    /**
     * Выполняет валидацию тела ответа на основе json схемы заданной в файле конфигурации для его метода и пути
     * @param httpMethod http метод
     * @param body тело запроса
     * @param path путь запроса
     * @return тело ответа
     */
    public String validate(String httpMethod, String body, String path) {
        JsonNode jsonNode;
        try {
            jsonNode = mapper.readTree(body);
        } catch (JsonProcessingException e) {
            log.error("Can't parse response body. Error: {}", e.getMessage());
            List<String> exceptions = new ArrayList<>(Collections.singletonList(e.getMessage()));
            throw new ValidationException(SERVER_ERROR_MESSAGE, exceptions, HttpStatus.INTERNAL_SERVER_ERROR);
        }
        if (responsesSchema.containsKey(httpMethod)) {
            Map<String, JsonSchema> jsonSchemaMapForCurrentMethod = responsesSchema.get(httpMethod);
            ModifiedPath modifiedPath = isSchemaMapContainsPath(jsonSchemaMapForCurrentMethod, path);
            if (modifiedPath.isSchemaMapContainsPath()) {
                path = modifiedPath.isPathModified() ? modifiedPath.getModifiedPath() : path;
                JsonSchema jsonSchema = jsonSchemaMapForCurrentMethod.get(path);
                Set<ValidationMessage> validate = jsonSchema.validate(jsonNode);
                if (validate.isEmpty()) {
                    log.debug("No validation errors in response {}, {}", httpMethod, path);
                } else {
                    List<String> validationErrors = validate.stream().map(Object::toString).collect(Collectors.toList());
                    log.error("Validation errors in response: {}", validationErrors);
                    throw new ValidationException(SERVER_ERROR_MESSAGE,
                            Collections.singletonList(SERVER_DETAILS_MESSAGE), HttpStatus.INTERNAL_SERVER_ERROR);
                }
            } else {
                log.info("No JSON schema for path {} (method - {}).", path, httpMethod);
                throw new ValidationException(SERVER_ERROR_MESSAGE,
                        Collections.singletonList(SERVER_DETAILS_MESSAGE), HttpStatus.INTERNAL_SERVER_ERROR);
            }
        } else {
            log.info("No JSON schema for method {} (path - {}).", httpMethod, path);
            throw new ValidationException(SERVER_ERROR_MESSAGE,
                    Collections.singletonList(SERVER_DETAILS_MESSAGE), HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return body;
    }
}
