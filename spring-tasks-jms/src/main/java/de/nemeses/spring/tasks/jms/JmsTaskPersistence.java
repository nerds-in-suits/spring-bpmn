package de.nemeses.spring.tasks.jms;

import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.nemeses.spring.tasks.TaskPersistence;
import jakarta.annotation.PreDestroy;
import jakarta.jms.Connection;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.MessageConsumer;
import jakarta.jms.MessageProducer;
import jakarta.jms.Session;
import jakarta.jms.TextMessage;

/**
 * {@link TaskPersistence} backed by a JMS queue.
 * Tasks are sent as text messages, the earliest execution time is mapped to the JMS 2.0 delivery delay
 * (<code>JMSDeliveryTime</code>). Received tasks are acknowledged on {@link TaskInfo#complete()} and
 * redelivered after {@link TaskInfo#fail(Exception)}.
 */
public class JmsTaskPersistence implements TaskPersistence {

    static final String PROPERTY_BEAN_NAME = "beanName";
    static final String PROPERTY_EARLIEST_EXECUTION = "earliestExecution";

    private static final long POLL_TIMEOUT_MILLIS = 500;

    private final Logger log = LoggerFactory.getLogger(this.getClass());
    private final ConnectionFactory connectionFactory;
    private final String queueName;
    private Connection sharedConnection;

    JmsTaskPersistence(final ConnectionFactory connectionFactory, final String queueName) {
        this.connectionFactory = connectionFactory;
        this.queueName = queueName;
    }

    @Override
    public void persist(final String beanName, final String payload, final Date earliestExecution) {
        try (Session session = this.openConnection().createSession(Session.AUTO_ACKNOWLEDGE);
                MessageProducer producer = session.createProducer(session.createQueue(this.queueName))) {
            final TextMessage message = session.createTextMessage(payload);
            message.setStringProperty(PROPERTY_BEAN_NAME, beanName);
            if (earliestExecution != null) {
                message.setLongProperty(PROPERTY_EARLIEST_EXECUTION, earliestExecution.getTime());
                final long delay = earliestExecution.getTime() - System.currentTimeMillis();
                if (delay > 0) {
                    producer.setDeliveryDelay(delay);
                }
            }
            producer.send(message);
        } catch (final JMSException e) {
            throw new JmsTaskException("Failed to send task " + beanName + " to JMS queue " + this.queueName, e);
        }
    }

    @Override
    @SuppressWarnings("PMD.CloseResource")
    public TaskInfo next() throws InterruptedException {
        Session session = null;
        try {
            session = this.openConnection().createSession(Session.CLIENT_ACKNOWLEDGE);
            final MessageConsumer consumer = session.createConsumer(session.createQueue(this.queueName));
            while (!Thread.currentThread().isInterrupted()) {
                final Message message = consumer.receive(POLL_TIMEOUT_MILLIS);
                if (message instanceof TextMessage text) {
                    return new JmsTaskInfo(session, text);
                }
                if (message != null) {
                    this.log.warn("Ignoring unsupported JMS message {}", message.getJMSMessageID());
                    message.acknowledge();
                }
            }
        } catch (final JMSException e) {
            closeQuietly(session);
            if (isInterruption(e)) {
                Thread.currentThread().interrupt();
                final InterruptedException interruption =
                    new InterruptedException("Interrupted while waiting for the next task");
                interruption.initCause(e);
                throw interruption;
            }
            throw new JmsTaskException("Failed to receive task from JMS queue " + this.queueName, e);
        }
        closeQuietly(session);
        throw new InterruptedException("Interrupted while waiting for the next task");
    }

    private static boolean isInterruption(final Throwable error) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current instanceof InterruptedException) {
                return true;
            }
        }
        return false;
    }

    private void closeQuietly(final Session session) {
        if (session != null) {
            try {
                session.close();
            } catch (final JMSException e) {
                this.log.warn("Failed to close JMS session", e);
            }
        }
    }

    @SuppressWarnings("PMD.CloseResource")
    private synchronized Connection openConnection() throws JMSException {
        if (this.sharedConnection == null) {
            final Connection created = this.connectionFactory.createConnection();
            created.start();
            this.sharedConnection = created;
        }
        return this.sharedConnection;
    }

    /**
     * Closes the shared JMS connection on bean destruction.
     */
    @PreDestroy
    synchronized void shutdown() {
        if (this.sharedConnection != null) {
            try {
                this.sharedConnection.close();
            } catch (final JMSException e) {
                this.log.warn("Failed to close JMS connection", e);
            }
            this.sharedConnection = null;
        }
    }

    /**
     * {@link TaskInfo} of a received JMS message, owning the session the message was received with.
     */
    private final class JmsTaskInfo implements TaskInfo {

        private final Logger taskLog = LoggerFactory.getLogger(this.getClass());
        private final Session session;
        private final TextMessage message;
        private final String name;
        private final String body;
        private final Date notBefore;

        JmsTaskInfo(final Session session, final TextMessage message) throws JMSException {
            this.session = session;
            this.message = message;
            this.name = message.getStringProperty(PROPERTY_BEAN_NAME);
            this.body = message.getText();
            this.notBefore = message.propertyExists(PROPERTY_EARLIEST_EXECUTION)
                ? new Date(message.getLongProperty(PROPERTY_EARLIEST_EXECUTION)) : null;
        }

        @Override
        public String beanName() {
            return this.name;
        }

        @Override
        public String payload() {
            return this.body;
        }

        @Override
        public Date earliestExecution() {
            return this.notBefore;
        }

        @Override
        public void complete() {
            try {
                this.message.acknowledge();
                this.session.close();
            } catch (final JMSException e) {
                throw new JmsTaskException("Failed to acknowledge task " + this.name, e);
            }
        }

        @Override
        public void fail(final Exception error) {
            this.taskLog.warn("Task {} failed, it will be redelivered", this.name, error);
            try {
                this.session.recover();
                this.session.close();
            } catch (final JMSException e) {
                throw new JmsTaskException("Failed to roll back task " + this.name, e);
            }
        }
    }
}
