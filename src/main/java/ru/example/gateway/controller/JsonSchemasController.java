package ru.example.gateway.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.PathContainer;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Контроллер отдает строковое представление схемы для проверки на основе ее полного имени.
 * В случае изменения пути к эндпоинту необходимо изменить значение PARTS_FOR_SKIP.
 * Он необходим для корректного формирования полного пути файла так как в последнем присутствуют '/' в неизвестном заранее количестве
 */
@RestController
@RequestMapping("/jsonschema")
public class JsonSchemasController {
    private static final Integer PARTS_FOR_SKIP = 3;

    @Resource(name = "localJsonFiles")
    private Map<String, String> localJsonFiles;

    @GetMapping("/{fileName}/**")
    public ResponseEntity<String> get(@PathVariable(value = "fileName") String file, ServerHttpRequest request) {
        List<PathContainer.Element> elements = request.getPath().elements();
        String fileName = elements.stream().map(PathContainer.Element::value).skip(PARTS_FOR_SKIP).collect(Collectors.joining()).toLowerCase();
        if (localJsonFiles.containsKey(fileName)) {
            return new ResponseEntity<>(localJsonFiles.get(fileName), HttpStatus.OK);
        }
        return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
    }
}
