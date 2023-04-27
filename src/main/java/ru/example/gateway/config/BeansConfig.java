package ru.example.gateway.config;

import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;

import lombok.extern.slf4j.Slf4j;

import org.eclipse.jgit.api.errors.GitAPIException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;

import java.io.IOException;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;

import ru.example.gateway.service.LocalRepoService;

/**
 * Класс для конфигурации бинов. Формируются в порядке расположения (@DependsOn)
 */
@Configuration
@Slf4j
public class BeansConfig {

    LocalRepoService localRepoService;

    @Autowired
    public void setLocalRepoService(LocalRepoService localRepoService) {
        this.localRepoService = localRepoService;
    }

    /**
     * Вызывает {@link LocalRepoService} для обновления локального репозитория и считывания файлов из него
     *
     * @return Map с полными путями файлов json и их содержимым в строковом представлении
     */
    @RefreshScope
    @Bean(name = "localJsonFiles")
    public Map<String, String> localJsonFiles() {
        Map<String, String> mapWithJsonFiles;
        try {
            localRepoService.getRepoToLocal();
            mapWithJsonFiles = localRepoService.formTextSchemasFromLocalRepo();
        } catch (GitAPIException | IOException e) {
            throw new RuntimeException(e);
        }
        return mapWithJsonFiles;
    }

    /**
     * Считывает свойства из конфигурационного файла начинающиеся с ключа 'response'
     *
     * @return Map с методом, путем запроса и полным именем файла со схемой для проверки тела ответа
     */
    @RefreshScope
    @Bean(name = "responsesSchema")
    @ConfigurationProperties(prefix = "response")
    @DependsOn("localJsonFiles")
    public Map<String, Map<String, String>> responseSchema() {
        return new HashMap<>();
    }

    /**
     * Считывает свойства из конфигурационного файла начинающиеся с ключа 'request'
     *
     * @return Map с методом, путем запроса и полным именем файла со схемой для проверки тела запроса
     */
    @RefreshScope
    @Bean(name = "requestsSchema")
    @ConfigurationProperties(prefix = "request")
    @DependsOn("localJsonFiles")
    public Map<String, Map<String, String>> requestSchema() {
        return new HashMap<>();
    }

    /**
     * Формируем bean с методами, путями запросов и схемами валидации для ответов
     *
     * @return Map с методом, путем запроса и схемой для проверки тела ответа
     */
    @RefreshScope
    @CachePut(value = "responseSchema")
    @Bean(name = "responseSchemaMap")
    @DependsOn({"localJsonFiles", "requestsSchema", "responsesSchema"})
    public Map<String, Map<String, JsonSchema>> responseSchemaMap() {
        Map<String, Map<String, JsonSchema>> responseSchemaMapWithMethod = responseSchema().entrySet().parallelStream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue().entrySet().parallelStream()
                                .collect(Collectors.toMap(
                                        x -> formUrlPath(x.getKey()),
                                        x -> getJsonSchemaFromStringContent(localJsonFiles().get(x.getValue().toLowerCase()))
                                ))
                ));
        log.info("'responseSchemaMap' bean formed with paths: {}", responseSchemaMapWithMethod.keySet());
        return responseSchemaMapWithMethod;
    }

    /**
     * Формируем bean с методами, путями запросов и схемами валидации для запросов
     *
     * @return Map с методом, путем запроса и схемой для проверки тела запроса
     */
    @RefreshScope
    @CachePut(value = "requestsSchemas")
    @Bean(name = "requestSchemaMap")
    @DependsOn({"localJsonFiles", "requestsSchema", "responsesSchema"})
    public Map<String, Map<String, JsonSchema>> requestSchemaMap() {
        Map<String, Map<String, JsonSchema>> requestSchemaMapWithMethod = requestSchema().entrySet().parallelStream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue().entrySet().parallelStream()
                                .collect(Collectors.toMap(
                                        x -> formUrlPath(x.getKey()),
                                        x -> getJsonSchemaFromStringContent(localJsonFiles().get(x.getValue().toLowerCase()))
                                ))
                ));
        log.info("'requestSchemaMap' bean formed with paths: {}", requestSchemaMapWithMethod.keySet());
        return requestSchemaMapWithMethod;
    }

    /**
     * Фабрика для создания {@link JsonSchema}
     *
     * @param schemaContent строка содержащая схему валидации
     * @return {@link JsonSchema}
     */
    private JsonSchema getJsonSchemaFromStringContent(String schemaContent) {
        JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);

        return factory.getSchema(schemaContent);
    }

    /**
     * Превращает название свойств в путь запроса
     *
     * @param value строковое представление свойств
     * @return путь входящего запроса
     */
    private String formUrlPath(String value) {
        String replaceString = value.replace(".", "/");
        StringBuilder pathBuilder = new StringBuilder(replaceString);
        pathBuilder.insert(0, "/");
        return pathBuilder.toString().toLowerCase();
    }


}
