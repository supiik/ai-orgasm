package com.etactics.proxima.service.lambda;

import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.etactics.proxima.service.backend.playlist.PlaylistService;
import jakarta.validation.Validator;
import org.springframework.data.domain.Pageable;

import java.util.Map;

public class HelloHandler extends BaseHandler<Map<String, Object>> {

    private final PlaylistService playlistService;

    public HelloHandler() {
        var ctx = SpringContextHolder.get();
        this.playlistService = ctx.getBean(PlaylistService.class);
    }

    HelloHandler(PlaylistService playlistService, ObjectMapper mapper, Validator validator) {
        super(mapper, validator);
        this.playlistService = playlistService;
    }

    @Override
    protected Map<String, Object> execute(APIGatewayV2HTTPEvent event) {
        var playlists = playlistService.findAll(r -> r, Pageable.ofSize(10));
        return Map.of("message", "Hello from Lambda!", "totalPlaylists", playlists.getTotalElements());
    }
}
