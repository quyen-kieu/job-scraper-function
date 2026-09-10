package com.jobscraper.function;

import com.jobscraper.application.notification.DailyNotificationHandler;
import com.jobscraper.application.notification.DailyNotificationRequestedEvent;
import com.microsoft.azure.functions.BrokerAuthenticationMode;
import com.microsoft.azure.functions.BrokerProtocol;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.Cardinality;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.KafkaTrigger;
import org.springframework.stereotype.Component;

/**
 * Kafka-triggered function for the {@code notifications.daily} topic.
 *
 * <p>Checkpoint semantics: the Azure Kafka extension only commits the consumed offset after
 * {@link #run(String, ExecutionContext)} returns without throwing.</p>
 */
@Component
public class ResendEmailConsumerFunction {

    private final DailyNotificationHandler notificationHandler;

    public ResendEmailConsumerFunction(DailyNotificationHandler notificationHandler) {
        this.notificationHandler = notificationHandler;
    }

    @FunctionName("ResendEmailConsumerFunction")
    public void run(
            @KafkaTrigger(
                    name = "message",
                    topic = "%KAFKA_NOTIFICATIONS_DAILY_TOPIC%",
                    brokerList = "%KAFKA_BOOTSTRAP_SERVERS%",
                    consumerGroup = "%KAFKA_EMAIL_CONSUMER_GROUP%",
                    username = "%KAFKA_USERNAME%",
                    password = "%KAFKA_PASSWORD%",
                    authenticationMode = BrokerAuthenticationMode.PLAIN,
                    protocol = BrokerProtocol.SASLSSL,
                    cardinality = Cardinality.ONE,
                    dataType = "string")
            String message,
            final ExecutionContext context) throws Exception {
        context.getLogger().info("ResendEmailConsumerFunction received a notifications.daily message.");
        handleDailyNotificationRequest(message, context);
    }

    private void handleDailyNotificationRequest(String message, ExecutionContext context) throws Exception {
        String payloadMessage = KafkaMessageSupport.unwrap(message);
        DailyNotificationRequestedEvent event = notificationHandler.handle(payloadMessage);
        context.getLogger().info(
                "Processed daily notification request runId=" + event.runId()
                        + " summaryDate=" + event.summaryDate()
        );
    }
}

