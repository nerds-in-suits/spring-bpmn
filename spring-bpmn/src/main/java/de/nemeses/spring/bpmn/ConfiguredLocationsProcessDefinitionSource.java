package de.nemeses.spring.bpmn;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

/**
 * Resolves the resource patterns configured by {@code spring.bpmn.process-definition-locations}.
 */
@Component
class ConfiguredLocationsProcessDefinitionSource implements ProcessDefinitionSource {

    private final ResourcePatternResolver resolver;
    private final List<String> locations;

    ConfiguredLocationsProcessDefinitionSource(final ResourcePatternResolver resolver,
            @Value("${spring.bpmn.process-definition-locations:classpath*:/process-definitions/*.bpmn}")
            final List<String> locations) {
        this.resolver = resolver;
        this.locations = locations;
    }

    @Override
    public Stream<Resource> resources() {
        return this.locations.stream()
            .flatMap(this::retrieveResourcesOf);
    }

    private Stream<Resource> retrieveResourcesOf(final String location) {
        try {
            return Arrays.stream(this.resolver.getResources(location));
        } catch (final IOException e) {
            throw new IllegalStateException("Failed to retrieve Resources from '"+location+"'.", e);
        }
    }
}
