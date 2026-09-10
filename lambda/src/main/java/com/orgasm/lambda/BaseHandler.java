package com.orgasm.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orgasm.dynamo.contributor.ContributorDynamoRepository;
import com.orgasm.dynamo.tenant.DynamoTenantContext;
import com.orgasm.lambda.auth.AuthMode;
import com.orgasm.lambda.auth.CognitoJwtValidator;
import com.orgasm.lambda.auth.ContributorNotLinkedException;
import com.orgasm.lambda.auth.UnauthorizedException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Base for all Lambda Function URL handlers. Subclasses implement {@link #execute} and
 * optionally override {@link #successStatus} (default 200; use 201 for creation handlers) and
 * {@link #authMode} (default {@link AuthMode#AUTHENTICATED_WITH_CONTRIBUTOR} — override to
 * {@link AuthMode#PUBLIC} for unauthenticated endpoints, {@link AuthMode#AUTHENTICATED} for the
 * link endpoint, which needs a valid token but no linked Contributor yet).
 * <p>
 * Error mapping:
 * <ul>
 *   <li>{@link ConstraintViolationException} (bean validation) → 400</li>
 *   <li>{@link IOException} (bad JSON) → 400</li>
 *   <li>{@link UnauthorizedException} (missing/invalid/expired token) → 401</li>
 *   <li>{@link ContributorNotLinkedException} (valid token, no linked Contributor) → 403</li>
 *   <li>{@link NoSuchElementException} → 404</li>
 *   <li>{@link IllegalStateException} (workflow state conflict) → 409</li>
 *   <li>Everything else → 500</li>
 * </ul>
 */
public abstract class BaseHandler<T>
        implements RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {

    private static final Logger log = LoggerFactory.getLogger(BaseHandler.class);

    protected final ObjectMapper mapper;
    private final Validator validator;
    private final CognitoJwtValidator cognitoJwtValidator;
    private final ContributorDynamoRepository contributorRepository;

    /** Populated by {@link #authenticate} before {@link #execute} runs, whenever {@link #authMode} isn't {@link AuthMode#PUBLIC}. */
    protected String cognitoSub;
    protected String cognitoEmail;

    protected BaseHandler() {
        var ctx = SpringContextHolder.get();
        this.mapper = ctx.getBean(ObjectMapper.class);
        this.validator = ctx.getBean(Validator.class);
        this.cognitoJwtValidator = ctx.getBean(CognitoJwtValidator.class);
        this.contributorRepository = ctx.getBean(ContributorDynamoRepository.class);
    }

    protected BaseHandler(ObjectMapper mapper, Validator validator) {
        this.mapper = mapper;
        this.validator = validator;
        // Test seam: unit tests construct handlers via this constructor to exercise execute()
        // in isolation, never triggering the Spring context. Auth/tenant wiring is centralized
        // here in BaseHandler and tested once (BaseHandlerAuthTest) rather than re-mocked in
        // every one of the ~30 handler test classes; leaving these two null makes authenticate()
        // a no-op for handlers built this way, regardless of authMode().
        this.cognitoJwtValidator = null;
        this.contributorRepository = null;
    }

    /** Test seam for handlers/tests that need to exercise the real auth+tenant-resolution path without a live Spring context. */
    protected BaseHandler(ObjectMapper mapper, Validator validator, CognitoJwtValidator cognitoJwtValidator, ContributorDynamoRepository contributorRepository) {
        this.mapper = mapper;
        this.validator = validator;
        this.cognitoJwtValidator = cognitoJwtValidator;
        this.contributorRepository = contributorRepository;
    }

    @Override
    public final APIGatewayV2HTTPResponse handleRequest(APIGatewayV2HTTPEvent event, Context context) {
        log.info("{} {}", event.getRequestContext().getHttp().getMethod(), event.getRawPath());
        try {
            authenticate(event);
            return respond(successStatus(), execute(event));
        } catch (ConstraintViolationException e) {
            var errors = e.getConstraintViolations().stream()
                    .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                    .sorted()
                    .toList();
            return respond(400, Map.of("errors", errors));
        } catch (IOException e) {
            return respond(400, Map.of("error", "Invalid request body: " + e.getMessage()));
        } catch (UnauthorizedException e) {
            return respond(401, Map.of("error", e.getMessage()));
        } catch (ContributorNotLinkedException e) {
            return respond(403, Map.of("error", e.getMessage()));
        } catch (NoSuchElementException e) {
            return respond(404, Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return respond(409, Map.of("error", e.getMessage()));
        } catch (CallNotPermittedException e) {
            return respond(503, Map.of("error", "Service temporarily unavailable"));
        } catch (Exception e) {
            log.error("Handler error", e);
            return respond(500, Map.of("error", "Internal server error"));
        } finally {
            DynamoTenantContext.clear();
        }
    }

    /**
     * Validates the bearer token (unless {@link #authMode} is {@link AuthMode#PUBLIC}), and for
     * {@link AuthMode#AUTHENTICATED_WITH_CONTRIBUTOR} additionally resolves the Contributor
     * linked to the token's {@code sub} and sets {@link DynamoTenantContext} from it — cleared
     * unconditionally in {@link #handleRequest}'s {@code finally}.
     */
    private void authenticate(APIGatewayV2HTTPEvent event) {
        if (authMode() == AuthMode.PUBLIC || cognitoJwtValidator == null) {
            return;
        }
        var claims = cognitoJwtValidator.validate(bearerToken(event));
        this.cognitoSub = claims.sub();
        this.cognitoEmail = claims.email();

        if (authMode() == AuthMode.AUTHENTICATED_WITH_CONTRIBUTOR) {
            var contributor = contributorRepository.findByCognitoSub(claims.sub())
                    .filter(c -> c.getDeletedAt() == null)
                    .orElseThrow(() -> new ContributorNotLinkedException("No contributor linked to this account"));
            DynamoTenantContext.set(contributor.getTenantId());
        }
    }

    private static String bearerToken(APIGatewayV2HTTPEvent event) {
        Map<String, String> headers = event.getHeaders();
        String header = headers == null ? null : headers.entrySet().stream()
                .filter(e -> e.getKey().equalsIgnoreCase("authorization"))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
        if (header == null || !header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            throw new UnauthorizedException("Missing or malformed Authorization header");
        }
        return header.substring(7).trim();
    }

    protected abstract T execute(APIGatewayV2HTTPEvent event) throws Exception;

    protected int successStatus() {
        return 200;
    }

    /** Default requires a valid Cognito token linked to an existing Contributor. Override for public/link endpoints. */
    protected AuthMode authMode() {
        return AuthMode.AUTHENTICATED_WITH_CONTRIBUTOR;
    }

    protected <B> B parseBody(APIGatewayV2HTTPEvent event, Class<B> type) throws IOException {
        B body = mapper.readValue(event.getBody(), type);
        var violations = validator.validate(body);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
        return body;
    }

    /**
     * Function URLs have no route templating ({@code {id}} path variables), so path segments
     * are pulled out of the raw path manually. {@code fromEnd = 0} is the last segment,
     * {@code 1} the second-to-last, etc. — e.g. for {@code /api/v1/playlists/{id}/open},
     * the id is {@code pathSegment(event, 1)}.
     */
    protected String pathSegment(APIGatewayV2HTTPEvent event, int fromEnd) {
        String path = event.getRawPath();
        List<String> segments = List.of(path.split("/"));
        int index = segments.size() - 1 - fromEnd;
        if (index < 0 || index >= segments.size()) {
            throw new NoSuchElementException("Path segment not found: " + path);
        }
        return segments.get(index);
    }

    protected String queryParam(APIGatewayV2HTTPEvent event, String key) {
        Map<String, String> params = event.getQueryStringParameters();
        return params == null ? null : params.get(key);
    }

    /** Builds a Pageable from {@code page}/{@code size} query params, defaulting to unpaged. */
    protected Pageable pageable(APIGatewayV2HTTPEvent event) {
        Map<String, String> params = event.getQueryStringParameters();
        if (params == null || !params.containsKey("size")) {
            return Pageable.unpaged();
        }
        int size = Integer.parseInt(params.get("size"));
        int page = params.containsKey("page") ? Integer.parseInt(params.get("page")) : 0;
        return PageRequest.of(page, size);
    }

    /**
     * Spring's {@code Page}/{@code PageImpl} isn't directly Jackson-serializable outside a
     * Spring MVC context — its embedded {@code Pageable} throws on {@code Unpaged.getOffset()}.
     * Converts to the plain shape the OpenAPI spec (e.g. {@code PlaylistPage}) declares instead.
     */
    protected Map<String, Object> pageBody(Page<?> page) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("content", page.getContent());
        body.put("totalElements", page.getTotalElements());
        body.put("totalPages", page.getTotalPages());
        body.put("number", page.getNumber());
        body.put("size", page.getSize());
        return body;
    }

    protected APIGatewayV2HTTPResponse respond(int statusCode, Object body) {
        if (body == null) {
            return APIGatewayV2HTTPResponse.builder().withStatusCode(statusCode).build();
        }
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
