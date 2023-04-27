package ru.example.gateway.filter;

import com.fasterxml.jackson.core.JsonProcessingException;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DefaultDataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import org.reactivestreams.Publisher;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

import ru.example.gateway.service.implementation.ResponseValidationService;
import ru.example.gateway.model.ValidateActivator;

/**
 * Реализация {@link GlobalFilter}, обрабатывает все исходящие ответы согласно условию
 */
@Configuration
@Slf4j
public class ResponseGlobalFilter implements WebFilter, Ordered {
    /**
     * ответ от {@link RequestGlobalFilter}
     */
    private static final String ERROR_RESPONSE = "{\"status\":";

    private ValidateActivator validateActivator;

    private ResponseValidationService responseValidationService;

    @Autowired
    public void setValidateActivator(ValidateActivator validateActivator) {
        this.validateActivator = validateActivator;
    }

    @Autowired
    public void setResponseSchemasService(ResponseValidationService responseValidationService) {
        this.responseValidationService = responseValidationService;
    }

    /**
     * Реализация метода фильтра ответов. Может быть включен или выключен {@link ValidateActivator}
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().toString();
        ServerHttpResponse response = exchange.getResponse();
        ServerHttpRequest request = exchange.getRequest();
        DataBufferFactory dataBufferFactory = response.bufferFactory();
        MediaType responseContentType = response.getHeaders().getContentType();
        System.out.println(responseContentType);
        if (validateActivator.isResponseOn()) {
            ServerHttpResponseDecorator decoratedResponse = getDecoratedResponse(path, response, request, dataBufferFactory, exchange);
            return chain.filter(exchange.mutate().response(decoratedResponse).build());
        } else {
            log.debug("Validate response off");
            return chain.filter(exchange);
        }
    }

    /**
     * Получает и валидирует тело ответа
     */
    private ServerHttpResponseDecorator getDecoratedResponse(String path, ServerHttpResponse response, ServerHttpRequest request, DataBufferFactory dataBufferFactory, ServerWebExchange exchange) {
        return new ServerHttpResponseDecorator(response) {

            @Override
            public Mono<Void> writeWith(final Publisher<? extends DataBuffer> body) {

                if (body instanceof Flux) {
                    Flux<? extends DataBuffer> fluxBody = (Flux<? extends DataBuffer>) body;
                    return super.writeWith(fluxBody.buffer().map(dataBuffers -> {

                        DefaultDataBuffer joinedBuffers = new DefaultDataBufferFactory().join(dataBuffers);
                        byte[] content = new byte[joinedBuffers.readableByteCount()];
                        joinedBuffers.read(content);
                        String responseBody = new String(content, StandardCharsets.UTF_8);
                        log.debug("requestId: {}, method: {}, url: {}, \nresponse body :{}", request.getId(), request.getMethodValue(), request.getURI(), responseBody);
                        MediaType responseContentType = exchange.getResponse().getHeaders().getContentType();
                        // Проверяем: если ответ от фильтра входящих запросов, то сразу пропускаем его. Иначе начинаем валидацию
                        if (responseBody.startsWith(ERROR_RESPONSE)) {
                            log.debug("Skip validation. Error in request validator filter");
                            return dataBufferFactory.wrap(responseBody.getBytes());
                        }
                        if (!MediaType.APPLICATION_JSON.equalsTypeAndSubtype(responseContentType)) {
                            log.debug("Skip validation. Response content type is {}", responseContentType);
                            return dataBufferFactory.wrap(responseBody.getBytes());
                        }
                        String method = request.getMethodValue().toLowerCase();
                        String result = responseValidationService.validate(method, responseBody, path);
                        return dataBufferFactory.wrap(result.getBytes());


                    })).onErrorResume(err -> {
                        // в случае возникновении ошибок при валидации - обрабатываем их и возвращаем пользователю ошибку сервера
                        DataBuffer exception;
                        try {
                            exception = responseValidationService.errorHandling(err, exchange);
                        } catch (JsonProcessingException e) {
                            return Mono.error(e);
                        }
                        return exchange.getResponse().writeWith(Flux.just(exception));
                    });

                }
                return super.writeWith(body);
            }
        };
    }

    @Override
    public int getOrder() {
        return -1;
    }

}