package com.algolens.config;

import java.io.IOException;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * Serves the built React bundle from {@code classpath:/static/}, with a single-page-app
 * fallback.
 *
 * <p>This is what makes the one-container deployment possible: the Docker build drops
 * {@code frontend/dist} into the jar, and the same service answers both the API and the UI. One
 * thing to deploy, one URL, and CORS never enters the picture -- which matters a lot on free
 * hosting, where every extra service is another cold start and another thing to keep alive.
 *
 * <p>The fallback exists because the SPA owns its own routes. A browser asking for
 * {@code /dashboard} is not asking for a file; it wants {@code index.html}, after which React
 * Router takes over. Paths that belong to the server are excluded so a mistyped API call still
 * returns a JSON 404 rather than a page of HTML, which would be far more confusing to debug.
 *
 * <p>When no bundle is present -- running the backend alone in development, with Vite serving
 * the UI on its own port -- every path simply 404s, which is correct.
 */
@Configuration
public class SpaConfig implements WebMvcConfigurer {

    private static final String[] SERVER_OWNED_PREFIXES = {
        "api/", "actuator/", "v3/api-docs", "swagger-ui", "h2-console",
    };

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location)
                            throws IOException {
                        Resource requested = location.createRelative(resourcePath);
                        if (requested.exists() && requested.isReadable()) {
                            return requested;
                        }
                        if (isServerOwned(resourcePath)) {
                            return null;
                        }
                        Resource index = new ClassPathResource("/static/index.html");
                        return index.exists() ? index : null;
                    }
                });
    }

    private static boolean isServerOwned(String resourcePath) {
        for (String prefix : SERVER_OWNED_PREFIXES) {
            if (resourcePath.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
