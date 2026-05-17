package com.orgasm.backend.config;

import org.springframework.http.server.PathContainer;
import org.springframework.web.accept.DefaultApiVersionStrategy;
import org.springframework.web.accept.ApiVersionStrategy;
import org.springframework.web.accept.PathApiVersionResolver;
import org.springframework.web.accept.SemanticApiVersionParser;

import java.util.List;

public final class VersionTestSupport {

    private VersionTestSupport() {}

    public static ApiVersionStrategy pathVersionStrategy() {
        return new DefaultApiVersionStrategy(
                List.of(new PathApiVersionResolver(0, path ->
                        path.pathWithinApplication().elements().stream()
                                .filter(e -> e instanceof PathContainer.PathSegment)
                                .findFirst()
                                .map(e -> e.value().matches("v\\d+.*"))
                                .orElse(false))),
                new SemanticApiVersionParser(),
                false, null, true, null, null);
    }
}
