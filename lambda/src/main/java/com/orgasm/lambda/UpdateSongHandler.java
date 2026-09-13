package com.orgasm.lambda;

import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orgasm.dynamo.song.SongResponse;
import com.orgasm.dynamo.song.SongService;
import com.orgasm.dynamo.song.UpdateSongRequest;
import jakarta.validation.Validator;

public class UpdateSongHandler extends BaseHandler<SongResponse> {

    private final SongService songService;

    public UpdateSongHandler() {
        var ctx = SpringContextHolder.get();
        this.songService = ctx.getBean(SongService.class);
    }

    UpdateSongHandler(SongService songService, ObjectMapper mapper, Validator validator) {
        super(mapper, validator);
        this.songService = songService;
    }

    @Override
    protected SongResponse execute(APIGatewayV2HTTPEvent event) throws Exception {
        String id = pathSegment(event, 0);
        var request = parseBody(event, UpdateSongRequest.class);
        return songService.update(id, request);
    }
}
