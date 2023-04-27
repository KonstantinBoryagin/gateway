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
import java.util.Set;
import java.util.Collections;
import java.util.stream.Collectors;

import ru.example.gateway.config.exception.ValidationException;
import ru.example.gateway.model.ModifiedPath;
import ru.example.gateway.service.IValidationService;

/**
 * Реализация {@link IValidationService} для входящих запросов
 */
@Service
@Slf4j
public class RequestValidationService implements IValidationService {
    public static final String VALIDATION_ERROR = "validation error";
    public static final String DESERIALIZE_ERROR = "deserialization error";
    public static final String NO_SCHEME_ERROR = "can't find json validation scheme";

    //Bean в котором хранятся пути запросов и схемы валидации для них
    @Resource(name = "requestSchemaMap")
    private Map<String, Map<String, JsonSchema>> requestsSchemaMap;
    private ObjectMapper mapper;

    @Autowired
    public void setMapper(ObjectMapper mapper) {
        this.mapper = mapper;
    }


    /**
     * Выполняет валидацию тела запроса на основе json схемы заданной в файле конфигурации для его метода и пути
     *
     * @param httpMethod http метод
     * @param body       тело запроса
     * @param path       путь запроса
     */
    public String validate(String httpMethod, String body, String path) {
        if (requestsSchemaMap.containsKey(httpMethod)) {
            Map<String, JsonSchema> requestSchemaMapForCurrentMethod = requestsSchemaMap.get(httpMethod);
            ModifiedPath modifiedPath = isSchemaMapContainsPath(requestSchemaMapForCurrentMethod, path);
            if (modifiedPath.isSchemaMapContainsPath()) {
                path = modifiedPath.isPathModified() ? modifiedPath.getModifiedPath() : path;
                JsonNode jsonNode;
                try {
                    jsonNode = mapper.readTree(body);
                } catch (JsonProcessingException e) {
                    //если не получается распарсить тело запроса - возвращаем ошибку
                    log.error("Can't parse request body. Error: {}", e.getMessage());
                    List<String> exceptions = new ArrayList<>(Collections.singletonList(e.getMessage()));
                    throw new ValidationException(DESERIALIZE_ERROR, exceptions, HttpStatus.BAD_REQUEST);
                }
                JsonSchema schema = requestSchemaMapForCurrentMethod.get(path);

                //выполняем валидацию и пропускаем запрос дальше или возвращаем в ответ ошибку
                Set<ValidationMessage> validate = schema.validate(jsonNode);

                if (validate.isEmpty()) {
                    log.debug("No validation errors in request {}, {}", httpMethod, path);
                } else {
                    List<String> validationErrors = validate.stream().map(Object::toString).collect(Collectors.toList());
                    log.error("Validation errors in request: {}", validationErrors);
                    throw new ValidationException(VALIDATION_ERROR, validationErrors, HttpStatus.PRECONDITION_FAILED);
                }
            } else {
                log.info("No JSON schema for path {} (method - {}).", path, httpMethod);
                throw new ValidationException(VALIDATION_ERROR, Collections.singletonList(NO_SCHEME_ERROR), HttpStatus.PRECONDITION_FAILED);
            }
        } else {
            log.info("No JSON schema for method {} (path - {}).", httpMethod, path);
            throw new ValidationException(VALIDATION_ERROR, Collections.singletonList(NO_SCHEME_ERROR), HttpStatus.PRECONDITION_FAILED);
        }
        return "";
    }
}
