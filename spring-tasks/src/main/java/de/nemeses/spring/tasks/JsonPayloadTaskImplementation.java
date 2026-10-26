package de.nemeses.spring.tasks;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 *
 */
public abstract class JsonPayloadTaskImplementation<T> implements TaskImplementation<T> {

    private final ObjectMapper mapper;

    protected JsonPayloadTaskImplementation() {
        this(new ObjectMapper());
    }

    protected JsonPayloadTaskImplementation(final ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public String serializePayload(final T payload) {
        try {
            return this.mapper.writeValueAsString(payload);
        } catch (final JacksonException e) {
            throw new PayloadException("Failed to serialize payload.", e);
        }
    }

    @Override
    public T deserializePayload(final String serializedPayload) {
        try {
            return this.mapper.readValue(serializedPayload, this.payloadType());
        } catch (final JacksonException e) {
            throw new PayloadException("Failed to deserialize payload.", e);
        }
    }

    abstract Class<T> payloadType();

}
