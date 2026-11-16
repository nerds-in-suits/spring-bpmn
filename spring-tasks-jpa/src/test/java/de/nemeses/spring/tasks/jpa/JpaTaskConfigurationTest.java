package de.nemeses.spring.tasks.jpa;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import de.nemeses.spring.tasks.TaskConfiguration;
import de.nemeses.spring.tasks.TaskPersistence;
import de.nemeses.spring.tasks.TaskService;
import de.nemeses.spring.tasks.jpa.application.TestApplication;

/**
 * Starts {@link TaskConfiguration} together with the auto-configurations: the {@link TaskService} uses the
 * {@link JpaTaskPersistence}, because the component scan of {@link TaskConfiguration} does not pick up
 * {@link JpaTaskPersistenceAutoConfiguration} before an {@code EntityManagerFactory} exists.
 */
@SpringBootTest(classes = { TaskConfiguration.class, TestApplication.class },
        properties = { "spring.jpa.hibernate.ddl-auto=create-drop", "spring.tasks.pool-size=0" })
class JpaTaskConfigurationTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void testJpaTaskPersistenceIsUsed() {
        Assertions.assertInstanceOf(JpaTaskPersistence.class, this.context.getBean(TaskPersistence.class));
        Assertions.assertNotNull(this.context.getBean(TaskService.class));
    }
}
