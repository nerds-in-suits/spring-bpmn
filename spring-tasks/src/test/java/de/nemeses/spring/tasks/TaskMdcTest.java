package de.nemeses.spring.tasks;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

/**
 *
 */
class TaskMdcTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void scopeRemovesKeyThatWasNotSet() {
        try (TaskMdc.Scope _ = TaskMdc.put(TaskMdc.TASK_ID, "1")) {
            Assertions.assertEquals("1", MDC.get(TaskMdc.TASK_ID), "set within the scope");
        }
        Assertions.assertNull(MDC.get(TaskMdc.TASK_ID), "removed after the scope");
    }

    @Test
    void nestedScopesRestorePreviousValues() {
        MDC.put("other", "kept");
        try (TaskMdc.Scope _ = TaskMdc.put(TaskMdc.TASK_ID, "outer")) {
            final Map<String, String> inner = new HashMap<>();
            inner.put(TaskMdc.TASK_ID, "inner");
            inner.put("other", null);
            try (TaskMdc.Scope _ = TaskMdc.put(inner)) {
                Assertions.assertEquals("inner", MDC.get(TaskMdc.TASK_ID), "inner value set");
                Assertions.assertNull(MDC.get("other"), "null value removes the key");
            }
            Assertions.assertEquals("outer", MDC.get(TaskMdc.TASK_ID), "outer value restored");
            Assertions.assertEquals("kept", MDC.get("other"), "removed key restored");
        }
        Assertions.assertNull(MDC.get(TaskMdc.TASK_ID), "removed after the outer scope");
        Assertions.assertEquals("kept", MDC.get("other"), "other key untouched");
    }
}
