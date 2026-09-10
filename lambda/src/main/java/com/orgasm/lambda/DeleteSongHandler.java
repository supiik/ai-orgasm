package com.orgasm.lambda;

import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orgasm.dynamo.song.SongService;
import jakarta.validation.Validator;

public class DeleteSongHandler extends BaseHandler<Void> {

    private final SongService songService;

    public DeleteSongHandler() {
        var ctx = SpringContextHolder.get();
        this.songService = ctx.getBean(SongService.class);
    }

    DeleteSongHandler(SongService songService, ObjectMapper mapper, Validator validator) {
        super(mapper, validator);
        this.songService = songService;
    }

    @Override
    protected Void execute(APIGatewayV2HTTPEvent event) {
        songService.delete(pathSegment(event, 0));
        return null;
    }

    @Override
    protected int successStatus() {
        return 204;
    }
}
