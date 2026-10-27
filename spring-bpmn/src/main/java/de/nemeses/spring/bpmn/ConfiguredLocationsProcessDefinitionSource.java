package de.nemeses.spring.bpmn;

import java.io.IOException;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

/**
 * Resolves the resource patterns configured by {@code spring.bpmn.process-definition-locations}.
 */
@Component
class ConfiguredLocationsProcessDefinitionSource implements ProcessDefinitionSource {

    private static final Comparator<Resource> BY_IDENTITY = Comparator.comparing(
            ConfiguredLocationsProcessDefinitionSource::identity);

    private final ResourcePatternResolver resolver;
    private final List<String> locations;

    ConfiguredLocationsProcessDefinitionSource(final ResourcePatternResolver resolver,
            @Value("${spring.bpmn.process-definition-locations:classpath*:/process-definitions/*.bpmn}")
            final List<String> locations) {
        this.resolver = resolver;
        this.locations = locations;
    }

    @Override
    public Collection<Resource> resources() throws IOException {
        final Collection<Resource> resources = new TreeSet<>(BY_IDENTITY);
        for (final String location : this.locations) {
            resources.addAll(List.of(this.resolver.getResources(location)));
        }
        return resources;
    }

    private static String identity(final Resource resource) {
        try {
            return resource.getURI().toString();
        } catch (final IOException e) {
            return resource.getDescription();
        }
    }
}
