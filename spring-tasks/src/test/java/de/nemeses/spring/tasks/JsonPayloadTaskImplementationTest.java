package de.nemeses.spring.tasks;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

public class JsonPayloadTaskImplementationTest {

    private final TestTaskImplementation testee = new TestTaskImplementation();

    @Test
    public void testRoundtrip() {
        this.testRoundtrip(TestPayload.generate());
        this.testRoundtrip(TestPayload.generate(10));
        this.testRoundtrip(null);
    }

    private void testRoundtrip(final TestPayload payload) {
        final String ser = this.testee.serializePayload(payload);
        LoggerFactory.getLogger(this.getClass()).debug("Serialized payload: {}", payload);
        final TestPayload deser = this.testee.deserializePayload(ser);
        Assertions.assertEquals(payload, deser);
    }
}
