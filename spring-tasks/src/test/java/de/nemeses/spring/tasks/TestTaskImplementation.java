package de.nemeses.spring.tasks;

import org.slf4j.LoggerFactory;

public class TestTaskImplementation extends JsonPayloadTaskImplementation<TestPayload> {

    @Override
    protected Class<TestPayload> payloadType() {
        return TestPayload.class;
    }

    @Override
    public void execute(final TestPayload payload) throws TaskException {
        LoggerFactory.getLogger(this.getClass()).info("Got execution for {}.", payload);
    }
}
