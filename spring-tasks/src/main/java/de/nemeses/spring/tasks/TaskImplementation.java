package de.nemeses.spring.tasks;

/**
 *
 */
public interface TaskImplementation<T> {

    void execute(T payload) throws TaskException;

    /**
     * Deserializes the given payload and executes this task with it.
     * @param serializedPayload the payload as returned by {@link #serializePayload(Object)}.
     * @throws TaskException if the execution failed.
     */
    default void executeSerialized(final String serializedPayload) throws TaskException {
        this.execute(this.deserializePayload(serializedPayload));
    }

    @SuppressWarnings("unchecked")
    default String serializePayloadObj(final Object payload) {
        return this.serializePayload((T) payload);
    }

    String serializePayload(T payload);

    T deserializePayload(String serializedPayload);
}
