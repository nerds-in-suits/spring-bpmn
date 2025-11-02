package de.nemeses.spring.tasks.jpa;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;
import org.springframework.transaction.PlatformTransactionManager;

import de.nemeses.spring.tasks.TaskPersistence;

/**
 *
 */
@AutoConfiguration(after = { JdbcTemplateAutoConfiguration.class, DataSourceTransactionManagerAutoConfiguration.class })
@ConditionalOnBean({ NamedParameterJdbcOperations.class, PlatformTransactionManager.class })
@ConditionalOnMissingBean(TaskPersistence.class)
public class JdbcTaskPersistenceAutoConfiguration {

    @Bean
    JdbcTaskPersistence taskPersistence(JdbcTemplate template) {
        return new JdbcTaskPersistence(template);
    }
}
