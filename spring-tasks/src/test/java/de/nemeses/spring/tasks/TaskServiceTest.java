package de.nemeses.spring.tasks;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 *
 */
@SpringBootTest(classes = {TaskConfiguration.class})
public class TaskServiceTest {

    @Autowired
    private TaskService taskService;

    @Test
    public void testSchedule() {
        this.taskService.trigger("testTaskImplementation", TestPayload.generate());
    }
}
