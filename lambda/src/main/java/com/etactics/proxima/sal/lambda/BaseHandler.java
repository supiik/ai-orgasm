package com.etactics.proxima.sal.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;

/**
 * Base for all Lambda Function URL handlers. Subclasses implement {@link #execute} and
 * optionally override {@link #successStatus} (default 200; use 201 for creation handlers).
 * <p>
 * Error mapping:
 * <ul>
 *   <li>{@link IOException} (bad JSON) → 400</li>
 *   <li>{@link ConstraintViolationException} (bean validation) → 400</li>
 *   <li>{@link EntityNotFoundException} → 404</li>
 *   <li>Everything else → 500</li>
 * </ul>
 */
public abstract class BaseHandler<T>
        implements RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {

    private static final Logger log = LoggerFactory.getLogger(BaseHandler.class);

    protected final ObjectMapper mapper;
    private final Validator validator;

    protected BaseHandler() {
        var ctx = SpringContextHolder.get();
        this.mapper = ctx.getBean(ObjectMapper.class);
        this.validator = ctx.getBean(Validator.class);
    }

    protected BaseHandler(ObjectMapper mapper, Validator validator) {
        this.mapper = mapper;
        this.validator = validator;
    }

    @Override
    public final APIGatewayV2HTTPResponse handleRequest(APIGatewayV2HTTPEvent event, Context context) {
        log.info("{} {}", event.getRequestContext().getHttp().getMethod(), event.getRawPath());
        try {
            return respond(successStatus(), execute(event));
        } catch (ConstraintViolationException e) {
            var errors = e.getConstraintViolations().stream()
                    .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                    .sorted()
                    .toList();
            return respond(400, Map.of("errors", errors));
        } catch (IOException e) {
            return respond(400, Map.of("error", "Invalid request body: " + e.getMessage()));
        } catch (EntityNotFoundException e) {
            return respond(404, Map.of("error", e.getMessage()));
        } catch (CallNotPermittedException e) {
            return respond(503, Map.of("error", "Service temporarily unavailable"));
        } catch (Exception e) {
            log.error("Handler error", e);
            return respond(500, Map.of("error", "Internal server error"));
        }
    }

    protected abstract T execute(APIGatewayV2HTTPEvent event) throws Exception;

    protected int successStatus() {
        return 200;
    }

    protected <B> B parseBody(APIGatewayV2HTTPEvent event, Class<B> type) throws IOException {
        B body = mapper.readValue(event.getBody(), type);
        var violations = validator.validate(body);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
        return body;
    }

    protected APIGatewayV2HTTPResponse respond(int statusCode, Object body) {
        try {
            return APIGatewayV2HTTPResponse.builder()
                    .withStatusCode(statusCode)
                    .withHeaders(Map.of("Content-Type", "application/json"))
                    .withBody(mapper.writeValueAsString(body))
                    .build();
        } catch (Exception e) {
            log.error("Serialization error", e);
            return APIGatewayV2HTTPResponse.builder()
                    .withStatusCode(500)
                    .withBody("{\"error\":\"Serialization failed\"}")
                    .build();
        }
    }
}
