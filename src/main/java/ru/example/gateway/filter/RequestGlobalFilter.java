package ru.example.gateway.filter;

import com.fasterxml.jackson.core.JsonProcessingException;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.codec.HttpMessageReader;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.HandlerStrategies;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

import ru.example.gateway.service.implementation.RequestValidationService;
import ru.example.gateway.model.ValidateActivator;

/**
 * Реализация {@link GlobalFilter}, обрабатывает все входящие запросы согласно условию
 */
@Slf4j
@Component
public class RequestGlobalFilter implements GlobalFilter, Ordered {

    /**
     * default HttpMessageReader
     */
    private final List<HttpMessageReader<?>> messageReaders = HandlerStrategies.withDefaults().messageReaders();

    private RequestValidationService requestValidationService;
    private ValidateActivator validateActivator;

    @Autowired
    public void setValidateActivator(ValidateActivator validateActivator) {
        this.validateActivator = validateActivator;
    }

    @Autowired
    public void setRequestValidationService(RequestValidationService requestValidationService) {
        this.requestValidationService = requestValidationService;
    }

    /**
     * Реализация метода фильтра входящих запросов. Может быть включен или выключен {@link ValidateActivator}
     * Применяется только в POST, PUT, PATCH запросах и при Content-Type = application/json
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        HttpMethod httpMethod = exchange.getRequest().getMethod();
        MediaType requestContentType = exchange.getRequest().getHeaders().getContentType();
        if (validateActivator.isRequestOn()) {
            if (MediaType.APPLICATION_JSON.equalsTypeAndSubtype(requestContentType)) {
                if (HttpMethod.POST.equals(httpMethod) || HttpMethod.PUT.equals(httpMethod) || HttpMethod.PATCH.equals(httpMethod)) {
                    return getRequestBody(exchange, chain);
                } else {
                    return chain.filter(exchange);
                }
            } else {
                log.debug("Skip validation. Request content type is {}", requestContentType);
                return chain.filter(exchange);
            }
        } else {
            log.debug("Validate request off");
            return chain.filter(exchange);
        }

    }

    /**
     * Метод получает тело запроса и в случае нахождения для него схемы в schemaMap выполняет валидацию
     */
    private Mono<Void> getRequestBody(ServerWebExchange exchange, GatewayFilterChain chain) {
        return DataBufferUtils.join(exchange.getRequest().getBody())
                .flatMap(dataBuffer -> {
                    DataBufferUtils.retain(dataBuffer);
                    Flux<DataBuffer> cachedFlux = Flux.defer(() -> Flux.just(dataBuffer.slice(0, dataBuffer.readableByteCount())));
                    ServerHttpRequest mutatedRequest = new ServerHttpRequestDecorator(exchange.getRequest()) {
                        @Override
                        public Flux<DataBuffer> getBody() {
                            return cachedFlux;
                        }
                    };

                    return ServerRequest
                            // создаем новый экземпляр exchange
                            .create(exchange.mutate().request(mutatedRequest).build(), messageReaders)
                            .bodyToMono(String.class)
                            .flatMap(requestBody -> {
                                // получаем путь запроса, метод и выполняем валидацию
                                String path = exchange.getRequest().getPath().pathWithinApplication().value();
                                String httpMethod = exchange.getRequest().getMethod().toString().toLowerCase();
                                requestValidationService.validate(httpMethod, requestBody, path);
                                // при успехе пропускаем запрос
                                return chain.filter(exchange.mutate().request(mutatedRequest).build());
                            }).onErrorResume(err -> {
                                DataBuffer errorDataBuffer;
                                // в случае возникновении ошибок при валидации - обрабатываем их и возвращаем ответ на запрос
                                try {
                                    errorDataBuffer = requestValidationService.errorHandling(err, exchange);
                                } catch (JsonProcessingException e) {
                                    return Mono.error(e);
                                }
                                return exchange.getResponse().writeWith(Flux.just(errorDataBuffer));
                            });
                });
    }

    @Override
    public int getOrder() {
        return -2;
    }
}
