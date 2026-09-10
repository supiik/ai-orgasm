package com.orgasm.lambda;

import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orgasm.dynamo.song.SongResponse;
import com.orgasm.dynamo.song.SongService;
import jakarta.validation.Validator;

import java.util.NoSuchElementException;

public class GetSongHandler extends BaseHandler<SongResponse> {

    private final SongService songService;

    public GetSongHandler() {
        var ctx = SpringContextHolder.get();
        this.songService = ctx.getBean(SongService.class);
    }

    GetSongHandler(SongService songService, ObjectMapper mapper, Validator validator) {
        super(mapper, validator);
        this.songService = songService;
    }

    @Override
    protected SongResponse execute(APIGatewayV2HTTPEvent event) {
        String id = pathSegment(event, 0);
        return songService.findById(id).orElseThrow(() -> new NoSuchElementException("Song not found: " + id));
    }
}
