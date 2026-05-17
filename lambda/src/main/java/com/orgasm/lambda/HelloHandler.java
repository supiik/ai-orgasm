package com.orgasm.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orgasm.backend.playlist.PlaylistService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;

import java.util.Map;

public class HelloHandler
        implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final Logger log = LoggerFactory.getLogger(HelloHandler.class);

    private final PlaylistService playlistService;
    private final ObjectMapper mapper;

    public HelloHandler() {
        var ctx = SpringContextHolder.get();
        this.playlistService = ctx.getBean(PlaylistService.class);
        this.mapper = ctx.getBean(ObjectMapper.class);
    }

    HelloHandler(PlaylistService playlistService, ObjectMapper mapper) {
        this.playlistService = playlistService;
        this.mapper = mapper;
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(
            APIGatewayProxyRequestEvent event, Context context) {

        log.info("Received event: path={}", event.getPath());

        try {
            var playlists = playlistService.findAll(Pageable.ofSize(10));
            var body = mapper.writeValueAsString(
                    Map.of("message", "Hello from Lambda!", "totalPlaylists", playlists.getTotalElements()));

            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(200)
                    .withHeaders(Map.of("Content-Type", "application/json"))
                    .withBody(body);
        } catch (Exception e) {
            log.error("Handler error", e);
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(500)
                    .withBody("{\"success\":false,\"message\":\"Internal error\"}");
        }
    }
}
