package com.example.birdlensapi.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Configuration
public class WebClientConfig {

    @Value("${app.ebird.api-key}")
    private String ebirdApiKey;

    @Value("${app.ebird.base-url}")
    private String ebirdBaseUrl;

    @Bean
    public WebClient ebirdWebClient(WebClient.Builder builder) {
        // Configure Netty HttpClient for strict connect and read timeouts
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
                .responseTimeout(Duration.ofSeconds(10))
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(10, TimeUnit.SECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(10, TimeUnit.SECONDS)));

        return builder
                .baseUrl(ebirdBaseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .filter(ebirdAuthFilter())
                .build();
    }

    private ExchangeFilterFunction ebirdAuthFilter() {
        return (request, next) -> {
            ClientRequest newRequest = ClientRequest.from(request)
                    .header("x-ebirdapitoken", ebirdApiKey)
                    .build();
            return next.exchange(newRequest);
        };
    }
}