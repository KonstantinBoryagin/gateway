package ru.example.gateway.controller;

import com.networknt.schema.JsonSchema;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.refresh.ContextRefresher;
import org.springframework.cloud.endpoint.RefreshEndpoint;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

/**
 * Контроллер для перехвата вэбхуков от GitLab с информацией о том что в репозитории с файлами свойств были изменения
 */
@RestController
@Slf4j
public class RefreshController extends RefreshEndpoint {

    @Resource(name = "requestSchemaMap")
    private Map<String, Map<String, JsonSchema>> requestsSchemas;

    @Resource(name = "responseSchemaMap")
    private Map<String, Map<String, JsonSchema>> responsesSchemas;

    private static final String TOKEN_HEADER = "X-Gitlab-Token";
    @Value("${gitlab.access_token}")
    private String gitLabAccessToken;

    public RefreshController(ContextRefresher contextRefresher) {
        super(contextRefresher);
    }

    /**
     * Эндпоинт получает запросы от GitLab, проверяет наличие и корректность заголовка с токеном доступа и либо
     * инициирует процесс обновления свойств приложения (через запрос новых у Configuration Server), либо
     * возвращает описание ошибки
     *
     * @param accessToken токен передаваемый GitLab для проверки подлинности запроса
     * @return результат запроса с сообщением об успехе/ошибке и Http статусом
     */
    @PostMapping("/refresh")
    public ResponseEntity<Collection<String>> refresh(@RequestHeader(value=TOKEN_HEADER, required = false) String accessToken) {

        if(accessToken == null){
            log.warn("Header {} is missing", TOKEN_HEADER);
            return new ResponseEntity<>(Collections.singletonList("Missing access token"), HttpStatus.FORBIDDEN);
        } else if(accessToken.equals(gitLabAccessToken)) {
            log.debug("Access token from header {} is correct", TOKEN_HEADER);
            Collection<String> refresh = super.refresh();
            log.info("Reload bean requestSchemaMap. Size - {}", requestsSchemas.size());
            log.info("Reload bean responseSchemaMap. Size - {}", responsesSchemas.size());

            return new ResponseEntity<>(refresh, HttpStatus.OK);
        } else {
            log.warn("Invalid access token in header {}", TOKEN_HEADER);
            return new ResponseEntity<>(Collections.singletonList("Invalid access token"), HttpStatus.FORBIDDEN);
        }
    }

}
