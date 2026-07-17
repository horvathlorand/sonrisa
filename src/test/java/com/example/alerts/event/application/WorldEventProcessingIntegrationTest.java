package com.example.alerts.event.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.alerts.alert.domain.Alert;
import com.example.alerts.alert.domain.AlertChannel;
import com.example.alerts.alert.domain.AlertStatus;
import com.example.alerts.alert.domain.ChannelType;
import com.example.alerts.alert.infrastructure.AlertRepository;
import com.example.alerts.event.domain.EventCategory;
import com.example.alerts.event.domain.EventType;
import com.example.alerts.event.domain.Severity;
import com.example.alerts.event.domain.WorldEvent;
import com.example.alerts.event.infrastructure.WorldEventRepository;
import com.example.alerts.notification.application.NotificationRetryService;
import com.example.alerts.notification.domain.DeliveryStatus;
import com.example.alerts.notification.domain.Notification;
import com.example.alerts.notification.domain.NotificationChannel;
import com.example.alerts.notification.domain.NotificationDelivery;
import com.example.alerts.notification.domain.NotificationResult;
import com.example.alerts.notification.infrastructure.NotificationDeliveryRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class WorldEventProcessingIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private WorldEventProcessingService processingService;

    @Autowired
    private NotificationRetryService retryService;

    @Autowired
    private AlertRepository alertRepository;

    @Autowired
    private WorldEventRepository worldEventRepository;

    @Autowired
    private NotificationDeliveryRepository deliveryRepository;

    @Autowired
    @Qualifier("emailRecordingChannel")
    private RecordingNotificationChannel emailChannel;

    @Autowired
    @Qualifier("slackRecordingChannel")
    private RecordingNotificationChannel slackChannel;

    @BeforeEach
    void cleanDatabase() {
        deliveryRepository.deleteAll();
        alertRepository.deleteAll();
        worldEventRepository.deleteAll();
        emailChannel.reset();
        slackChannel.reset();
    }

    @Test
    void processesMatchingAlertChannelsOutsideDatabaseTransaction() {
        alertRepository.save(activeAlert(ChannelType.EMAIL, "ops@example.com", ChannelType.SLACK, "#ops"));

        processingService.process(worldEvent("source-1", EventType.BREAKING_NEWS, EventCategory.NEWS, Severity.HIGH));

        assertThat(deliveryRepository.findAll())
            .extracting(NotificationDelivery::getStatus)
            .containsExactlyInAnyOrder(DeliveryStatus.SENT, DeliveryStatus.SENT);
        assertThat(emailChannel.sends()).isEqualTo(1);
        assertThat(slackChannel.sends()).isEqualTo(1);
        assertThat(emailChannel.transactionStates()).containsExactly(false);
        assertThat(slackChannel.transactionStates()).containsExactly(false);
    }

    @Test
    void disabledAlertsDoNotCreateDeliveries() {
        alertRepository.save(alert(AlertStatus.DISABLED, ChannelType.EMAIL, "ops@example.com"));

        processingService.process(worldEvent("source-disabled", EventType.BREAKING_NEWS, EventCategory.NEWS, Severity.CRITICAL));

        assertThat(deliveryRepository.findAll()).isEmpty();
        assertThat(emailChannel.sends()).isZero();
    }

    @Test
    void duplicateProcessingDoesNotCreateOrSendDuplicateDeliveries() {
        alertRepository.save(alert(AlertStatus.ACTIVE, ChannelType.EMAIL, "ops@example.com"));
        WorldEvent event = worldEvent("source-duplicate", EventType.BREAKING_NEWS, EventCategory.NEWS, Severity.HIGH);

        processingService.process(event);
        processingService.process(event);

        assertThat(deliveryRepository.findAll()).hasSize(1);
        assertThat(emailChannel.sends()).isEqualTo(1);
    }

    @Test
    void concurrentProcessingUsesDatabaseBackedDeliveryIdempotency() throws Exception {
        alertRepository.save(alert(AlertStatus.ACTIVE, ChannelType.EMAIL, "ops@example.com"));
        WorldEvent event = worldEventRepository.saveAndFlush(
            worldEvent("source-concurrent", EventType.BREAKING_NEWS, EventCategory.NEWS, Severity.HIGH)
        );
        int workers = 8;
        CountDownLatch ready = new CountDownLatch(workers);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(workers);

        List<java.util.concurrent.Future<?>> futures = new java.util.ArrayList<>();
        for (int i = 0; i < workers; i++) {
            futures.add(executor.submit(() -> {
                ready.countDown();
                start.await(5, TimeUnit.SECONDS);
                processingService.process(event);
                return null;
            }));
        }

        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        executor.shutdown();
        assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

        for (java.util.concurrent.Future<?> future : futures) {
            future.get();
        }

        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        executor.shutdown();
        assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

        assertThat(deliveryRepository.findAll()).hasSize(1);
        assertThat(emailChannel.sends()).isEqualTo(1);
    }

    @Test
    void channelFailuresArePersistedIndependently() {
        slackChannel.nextResult(NotificationResult.failed("slack unavailable"));
        alertRepository.save(activeAlert(ChannelType.EMAIL, "ops@example.com", ChannelType.SLACK, "#ops"));

        processingService.process(worldEvent("source-failure", EventType.BREAKING_NEWS, EventCategory.NEWS, Severity.HIGH));

        assertThat(deliveryRepository.findAll())
            .extracting(NotificationDelivery::getChannelType, NotificationDelivery::getStatus)
            .containsExactlyInAnyOrder(
                org.assertj.core.groups.Tuple.tuple(ChannelType.EMAIL, DeliveryStatus.SENT),
                org.assertj.core.groups.Tuple.tuple(ChannelType.SLACK, DeliveryStatus.FAILED)
            );
    }

    @Test
    void retryReusesExistingFailedDelivery() {
        emailChannel.nextResult(NotificationResult.failed("provider timeout"));
        alertRepository.save(alert(AlertStatus.ACTIVE, ChannelType.EMAIL, "ops@example.com"));
        processingService.process(worldEvent("source-retry", EventType.BREAKING_NEWS, EventCategory.NEWS, Severity.HIGH));
        NotificationDelivery failedDelivery = deliveryRepository.findAll().getFirst();

        boolean retried = retryService.retry(failedDelivery.getId());

        NotificationDelivery retriedDelivery = deliveryRepository.findById(failedDelivery.getId()).orElseThrow();
        assertThat(retried).isTrue();
        assertThat(retriedDelivery.getStatus()).isEqualTo(DeliveryStatus.SENT);
        assertThat(deliveryRepository.findAll()).hasSize(1);
        assertThat(emailChannel.sends()).isEqualTo(2);
    }

    @Test
    void databaseEnforcesDeliveryIdentityUniqueness() {
        Alert alert = alertRepository.save(alert(AlertStatus.ACTIVE, ChannelType.EMAIL, "ops@example.com"));
        WorldEvent event = worldEventRepository.saveAndFlush(
            worldEvent("source-unique", EventType.BREAKING_NEWS, EventCategory.NEWS, Severity.HIGH)
        );
        deliveryRepository.saveAndFlush(delivery(alert.getId(), event.getId(), ChannelType.EMAIL, "ops@example.com"));

        assertThatThrownBy(() -> deliveryRepository.saveAndFlush(
            delivery(alert.getId(), event.getId(), ChannelType.EMAIL, "ops@example.com")
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    private static Alert activeAlert(ChannelType firstType, String firstTarget, ChannelType secondType, String secondTarget) {
        return Alert.builder()
            .userId(UUID.randomUUID())
            .name("Critical operations")
            .eventType(EventType.BREAKING_NEWS)
            .category(EventCategory.NEWS)
            .minimumSeverity(Severity.MEDIUM)
            .status(AlertStatus.ACTIVE)
            .channels(new java.util.LinkedHashSet<>(List.of(
                AlertChannel.builder().channelType(firstType).target(firstTarget).build(),
                AlertChannel.builder().channelType(secondType).target(secondTarget).build()
            )))
            .build();
    }

    private static Alert alert(AlertStatus status, ChannelType channelType, String target) {
        return Alert.builder()
            .userId(UUID.randomUUID())
            .name("Critical operations")
            .eventType(EventType.BREAKING_NEWS)
            .category(EventCategory.NEWS)
            .minimumSeverity(Severity.MEDIUM)
            .status(status)
            .channels(new java.util.LinkedHashSet<>(List.of(
                AlertChannel.builder().channelType(channelType).target(target).build()
            )))
            .build();
    }

    private static WorldEvent worldEvent(
        String sourceEventId,
        EventType eventType,
        EventCategory category,
        Severity severity
    ) {
        return WorldEvent.builder()
            .sourceEventId(sourceEventId)
            .eventType(eventType)
            .category(category)
            .severity(severity)
            .title("Important event")
            .description("A normalized upstream event")
            .occurredAt(Instant.now())
            .build();
    }

    private static NotificationDelivery delivery(UUID alertId, UUID eventId, ChannelType channelType, String target) {
        return NotificationDelivery.builder()
            .alertId(alertId)
            .eventId(eventId)
            .channelType(channelType)
            .target(target)
            .build();
    }

    @TestConfiguration
    static class TestChannels {

        @Bean
        RecordingNotificationChannel emailRecordingChannel() {
            return new RecordingNotificationChannel(ChannelType.EMAIL);
        }

        @Bean
        RecordingNotificationChannel slackRecordingChannel() {
            return new RecordingNotificationChannel(ChannelType.SLACK);
        }
    }

    static class RecordingNotificationChannel implements NotificationChannel {

        private final ChannelType channelType;
        private final AtomicInteger sends = new AtomicInteger();
        private final ConcurrentLinkedQueue<NotificationResult> results = new ConcurrentLinkedQueue<>();
        private final CopyOnWriteArrayList<Boolean> transactionStates = new CopyOnWriteArrayList<>();

        RecordingNotificationChannel(ChannelType channelType) {
            this.channelType = channelType;
        }

        @Override
        public ChannelType channelType() {
            return channelType;
        }

        @Override
        public NotificationResult send(Notification notification) {
            sends.incrementAndGet();
            transactionStates.add(TransactionSynchronizationManager.isActualTransactionActive());
            NotificationResult result = results.poll();
            return result == null ? NotificationResult.sent() : result;
        }

        void nextResult(NotificationResult result) {
            results.add(result);
        }

        int sends() {
            return sends.get();
        }

        List<Boolean> transactionStates() {
            return List.copyOf(transactionStates);
        }

        void reset() {
            sends.set(0);
            results.clear();
            transactionStates.clear();
        }
    }
}
