package ru.example.gateway.config.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.client.oidc.web.server.logout.OidcClientInitiatedServerLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.logout.ServerLogoutHandler;
import org.springframework.security.web.server.authentication.logout.DelegatingServerLogoutHandler;
import org.springframework.security.web.server.authentication.logout.WebSessionServerLogoutHandler;
import org.springframework.security.web.server.authentication.logout.SecurityContextServerLogoutHandler;
import org.springframework.security.web.server.authentication.logout.ServerLogoutSuccessHandler;
import ru.example.gateway.controller.JsonSchemasController;
import ru.example.gateway.controller.RefreshController;

/**
 * Класс для настройки Spring Security
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

	@Value("${logout.url}")
	private String postLogoutUrl;

	private ReactiveClientRegistrationRepository clientRegistrationRepository;
	@Autowired
	public void setClientRegistrationRepository(ReactiveClientRegistrationRepository clientRegistrationRepository) {
		this.clientRegistrationRepository = clientRegistrationRepository;
	}

	/**
	 * Настройка безопасности: csrf отключен, все запросы должны быть аутентифицированные
	 * (исключение '/refresh' {@link RefreshController} - у него своя проверка
	 * и '/jsonschema/**' - {@link JsonSchemasController}),
	 * есть возможность oAuth2 аутентификации (с редиректом на страницу логина Keycloak и обратно)
	 * и возможность передачи уже полученного jwt токена с дальнейшей передачей его целевому сервису
	 */
	@Bean
	public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {

		http
				.csrf().disable()
				.authorizeExchange()
				.pathMatchers("/jsonschema/**", "/refresh").permitAll()
				.anyExchange().authenticated()
				.and()
                .oauth2Login(Customizer.withDefaults())
				.logout(logout -> logout.logoutHandler(logoutHandler()).logoutSuccessHandler(oidcLogoutSuccessHandler()))
				.oauth2ResourceServer()
				.jwt();
		return http.build();
	}

	//настройка logout, сейчас привязана к 'postLogoutUrl'/logout
	private ServerLogoutHandler logoutHandler() {
		return new DelegatingServerLogoutHandler(new WebSessionServerLogoutHandler(), new SecurityContextServerLogoutHandler());
	}
	private ServerLogoutSuccessHandler oidcLogoutSuccessHandler() {
		OidcClientInitiatedServerLogoutSuccessHandler logoutSuccessHandler = new OidcClientInitiatedServerLogoutSuccessHandler(clientRegistrationRepository);
		logoutSuccessHandler.setPostLogoutRedirectUri(postLogoutUrl);
		return logoutSuccessHandler;
	}

}