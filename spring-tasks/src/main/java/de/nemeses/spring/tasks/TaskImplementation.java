package de.nemeses.spring.tasks;

/**
 *
 */
public interface TaskImplementation<T> {

    void execute(T payload) throws TaskException;

    @SuppressWarnings("unchecked")
    default String serializePayloadObj(final Object payload) {
        return this.serializePayload((T) payload);
    }

    String serializePayload(T payload);

    T deserializePayload(String serializedPayload);
}
