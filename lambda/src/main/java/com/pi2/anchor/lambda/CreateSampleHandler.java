package com.pi2.anchor.lambda;

import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pi2.anchor.backend.sample.CreateSampleRequest;
import com.pi2.anchor.backend.sample.SampleResponse;
import com.pi2.anchor.backend.sample.SampleService;
import jakarta.validation.Validator;

public class CreateSampleHandler extends BaseHandler<SampleResponse> {

    private final SampleService sampleService;

    public CreateSampleHandler() {
        var ctx = SpringContextHolder.get();
        this.sampleService = ctx.getBean(SampleService.class);
    }

    CreateSampleHandler(SampleService sampleService, ObjectMapper mapper, Validator validator) {
        super(mapper, validator);
        this.sampleService = sampleService;
    }

    @Override
    protected SampleResponse execute(APIGatewayV2HTTPEvent event) throws Exception {
        var request = parseBody(event, CreateSampleRequest.class);
        return sampleService.create(request);
    }

    @Override
    protected int successStatus() {
        return 201;
    }
}
