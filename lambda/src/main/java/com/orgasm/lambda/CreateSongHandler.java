package com.orgasm.lambda;

import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orgasm.dynamo.song.CreateSongRequest;
import com.orgasm.dynamo.song.SongResponse;
import com.orgasm.dynamo.song.SongService;
import jakarta.validation.Validator;

public class CreateSongHandler extends BaseHandler<SongResponse> {

    private final SongService songService;

    public CreateSongHandler() {
        var ctx = SpringContextHolder.get();
        this.songService = ctx.getBean(SongService.class);
    }

    CreateSongHandler(SongService songService, ObjectMapper mapper, Validator validator) {
        super(mapper, validator);
        this.songService = songService;
    }

    @Override
    protected SongResponse execute(APIGatewayV2HTTPEvent event) throws Exception {
        var request = parseBody(event, CreateSongRequest.class);
        return songService.create(request);
    }

    @Override
    protected int successStatus() {
        return 201;
    }
}
