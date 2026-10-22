package de.nemeses.spring.tasks;

import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class TestTaskImplementation extends JsonPayloadTaskImplementation<TestPayload> {

    @Override
    Class<TestPayload> payloadType() {
        return TestPayload.class;
    }

    @Override
    public void execute(final TestPayload payload) throws TaskException {
        LoggerFactory.getLogger(this.getClass()).info("Got execution for {}.", payload);
    }
}
