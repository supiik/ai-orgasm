package com.orgasm.lambda;

import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orgasm.dynamo.orgasm.OrgasmService;
import com.orgasm.dynamo.orgasm.SongNominationResponse;
import jakarta.validation.Validator;

import java.util.List;

public class ListSongNominationsHandler extends BaseHandler<List<SongNominationResponse>> {

    private final OrgasmService orgasmService;

    public ListSongNominationsHandler() {
        var ctx = SpringContextHolder.get();
        this.orgasmService = ctx.getBean(OrgasmService.class);
    }

    ListSongNominationsHandler(OrgasmService orgasmService, ObjectMapper mapper, Validator validator) {
        super(mapper, validator);
        this.orgasmService = orgasmService;
    }

    @Override
    protected List<SongNominationResponse> execute(APIGatewayV2HTTPEvent event) {
        String songId = pathSegment(event, 1);
        return orgasmService.findNominationsBySong(songId);
    }
}
