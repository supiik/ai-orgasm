package com.orgasm.lambda;

import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orgasm.dynamo.song.SongService;
import jakarta.validation.Validator;

import java.util.Map;

public class ListSongsHandler extends BaseHandler<Map<String, Object>> {

    private final SongService songService;

    public ListSongsHandler() {
        var ctx = SpringContextHolder.get();
        this.songService = ctx.getBean(SongService.class);
    }

    ListSongsHandler(SongService songService, ObjectMapper mapper, Validator validator) {
        super(mapper, validator);
        this.songService = songService;
    }

    @Override
    protected Map<String, Object> execute(APIGatewayV2HTTPEvent event) {
        String name = queryParam(event, "name");
        return pageBody(songService.findAll(b -> b.name(name), pageable(event)));
    }
}
