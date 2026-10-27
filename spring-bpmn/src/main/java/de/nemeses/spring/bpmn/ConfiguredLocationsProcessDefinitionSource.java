package de.nemeses.spring.bpmn;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;

/**
 * Resolves the resource patterns configured by {@code spring.bpmn.process-definition-locations}.
 */
class ConfiguredLocationsProcessDefinitionSource implements ProcessDefinitionSource {

    private final ResourcePatternResolver resolver;
    private final List<String> locations;

    ConfiguredLocationsProcessDefinitionSource(final ResourcePatternResolver resolver, final List<String> locations) {
        this.resolver = resolver;
        this.locations = locations;
    }

    @Override
    public Collection<Resource> resources() throws IOException {
        final List<Resource> resources = new ArrayList<>();
        for (final String location : this.locations) {
            resources.addAll(List.of(this.resolver.getResources(location)));
        }
        return resources;
    }
}
