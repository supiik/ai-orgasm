package com.pi2.anchor.lambda;

import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pi2.anchor.backend.sample.SampleService;
import jakarta.validation.Validator;
import org.springframework.data.domain.Pageable;

import java.util.Map;

public class HelloHandler extends BaseHandler<Map<String, Object>> {

    private final SampleService sampleService;

    public HelloHandler() {
        var ctx = SpringContextHolder.get();
        this.sampleService = ctx.getBean(SampleService.class);
    }

    HelloHandler(SampleService sampleService, ObjectMapper mapper, Validator validator) {
        super(mapper, validator);
        this.sampleService = sampleService;
    }

    @Override
    protected Map<String, Object> execute(APIGatewayV2HTTPEvent event) {
        var samples = sampleService.findAll(r -> r, Pageable.ofSize(10));
        return Map.of("message", "Hello from Lambda!", "totalSamples", samples.getTotalElements());
    }
}
