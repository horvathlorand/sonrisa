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
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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
    void slowExternalProviderSeesCommittedPendingDeliveryOutsideDatabaseTransaction() throws Exception {
        CountDownLatch sendStarted = new CountDownLatch(1);
        CountDownLatch releaseSend = new CountDownLatch(1);
        emailChannel.blockNextSend(sendStarted, releaseSend);
        alertRepository.save(alert(AlertStatus.ACTIVE, ChannelType.EMAIL, "ops@example.com"));
        var executor = Executors.newSingleThreadExecutor();

        try {
            var future = executor.submit(() -> {
                processingService.process(
                    worldEvent("source-slow-provider", EventType.BREAKING_NEWS, EventCategory.NEWS, Severity.HIGH)
                );
                return null;
            });

            assertThat(sendStarted.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(emailChannel.transactionStates()).containsExactly(false);
            assertThat(deliveryRepository.findAll())
                .singleElement()
                .satisfies(delivery -> {
                    assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.PENDING);
                    assertThat(delivery.getClaimedAt()).isNotNull();
                    assertThat(delivery.getCompletedAt()).isNull();
                });

            releaseSend.countDown();
            future.get(5, TimeUnit.SECONDS);
        } finally {
            releaseSend.countDown();
            executor.shutdown();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(deliveryRepository.findAll())
            .singleElement()
            .extracting(NotificationDelivery::getStatus)
            .isEqualTo(DeliveryStatus.SENT);
    }

    @Test
    void disabledAlertsDoNotCreateDeliveries() {
        alertRepository.save(alert(AlertStatus.DISABLED, ChannelType.EMAIL, "ops@example.com"));

        processingService.process(worldEvent("source-disabled", EventType.BREAKING_NEWS, EventCategory.NEWS, Severity.CRITICAL));

        assertThat(deliveryRepository.findAll()).isEmpty();
        assertThat(emailChannel.sends()).isZero();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("nonMatchingAlertCases")
    void nonMatchingAlertsDoNotCreateDeliveriesOrSendNotifications(
        String caseName,
        EventType alertEventType,
        EventCategory alertCategory,
        Severity alertMinimumSeverity,
        EventType eventType,
        EventCategory eventCategory,
        Severity eventSeverity
    ) {
        alertRepository.save(
            alert(AlertStatus.ACTIVE, alertEventType, alertCategory, alertMinimumSeverity, ChannelType.EMAIL, "ops@example.com")
        );

        processingService.process(worldEvent("source-non-match-" + caseName, eventType, eventCategory, eventSeverity));

        assertThat(deliveryRepository.findAll()).isEmpty();
        assertThat(emailChannel.sends()).isZero();
        assertThat(slackChannel.sends()).isZero();
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
    void concurrentProcessingUsesDatabaseBackedEventAndDeliveryIdempotency() throws Exception {
        alertRepository.save(alert(AlertStatus.ACTIVE, ChannelType.EMAIL, "ops@example.com"));
        int workers = 8;
        CountDownLatch ready = new CountDownLatch(workers);
        CountDownLatch start = new CountDownLatch(1);
        ConcurrentLinkedQueue<Throwable> failures = new ConcurrentLinkedQueue<>();
        var executor = Executors.newFixedThreadPool(workers);

        List<java.util.concurrent.Future<?>> futures = new java.util.ArrayList<>();
        for (int i = 0; i < workers; i++) {
            futures.add(executor.submit(() -> {
                try {
                    ready.countDown();
                    if (!start.await(5, TimeUnit.SECONDS)) {
                        throw new AssertionError("Timed out waiting for concurrent start signal.");
                    }

                    processingService.process(
                        worldEvent("source-concurrent-unsaved", EventType.BREAKING_NEWS, EventCategory.NEWS, Severity.HIGH)
                    );
                } catch (Throwable throwable) {
                    failures.add(throwable);
                }
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

        assertThat(failures).isEmpty();
        assertThat(worldEventRepository.findAll())
            .extracting(WorldEvent::getSourceEventId)
            .containsExactly("source-concurrent-unsaved");
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
    void thrownChannelFailureIsPersistedWithoutRollingBackSuccessfulChannel() {
        slackChannel.nextException(new RuntimeException("slack unavailable"));
        alertRepository.save(activeAlert(ChannelType.EMAIL, "ops@example.com", ChannelType.SLACK, "#ops"));

        processingService.process(worldEvent("source-thrown-failure", EventType.BREAKING_NEWS, EventCategory.NEWS, Severity.HIGH));

        assertThat(deliveryRepository.findAll())
            .extracting(
                NotificationDelivery::getChannelType,
                NotificationDelivery::getStatus,
                NotificationDelivery::getFailureReason
            )
            .containsExactlyInAnyOrder(
                org.assertj.core.groups.Tuple.tuple(ChannelType.EMAIL, DeliveryStatus.SENT, null),
                org.assertj.core.groups.Tuple.tuple(ChannelType.SLACK, DeliveryStatus.FAILED, "slack unavailable")
            );
        assertThat(emailChannel.sends()).isEqualTo(1);
        assertThat(slackChannel.sends()).isEqualTo(1);
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
    void retryDoesNotResendSuccessfulDelivery() {
        alertRepository.save(alert(AlertStatus.ACTIVE, ChannelType.EMAIL, "ops@example.com"));
        processingService.process(worldEvent("source-retry-sent", EventType.BREAKING_NEWS, EventCategory.NEWS, Severity.HIGH));
        NotificationDelivery sentDelivery = deliveryRepository.findAll().getFirst();

        boolean retried = retryService.retry(sentDelivery.getId());

        NotificationDelivery unchangedDelivery = deliveryRepository.findById(sentDelivery.getId()).orElseThrow();
        assertThat(retried).isFalse();
        assertThat(unchangedDelivery.getStatus()).isEqualTo(DeliveryStatus.SENT);
        assertThat(deliveryRepository.findAll()).hasSize(1);
        assertThat(emailChannel.sends()).isEqualTo(1);
    }

    @Test
    void concurrentRetryClaimsExistingFailedDeliveryOnlyOnce() throws Exception {
        emailChannel.nextResult(NotificationResult.failed("provider timeout"));
        alertRepository.save(alert(AlertStatus.ACTIVE, ChannelType.EMAIL, "ops@example.com"));
        processingService.process(worldEvent("source-concurrent-retry", EventType.BREAKING_NEWS, EventCategory.NEWS, Severity.HIGH));
        NotificationDelivery failedDelivery = deliveryRepository.findAll().getFirst();
        int workers = 8;
        CountDownLatch ready = new CountDownLatch(workers);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successfulRetries = new AtomicInteger();
        ConcurrentLinkedQueue<Throwable> failures = new ConcurrentLinkedQueue<>();
        var executor = Executors.newFixedThreadPool(workers);

        List<java.util.concurrent.Future<?>> futures = new java.util.ArrayList<>();
        for (int i = 0; i < workers; i++) {
            futures.add(executor.submit(() -> {
                try {
                    ready.countDown();
                    if (!start.await(5, TimeUnit.SECONDS)) {
                        throw new AssertionError("Timed out waiting for concurrent retry start signal.");
                    }

                    if (retryService.retry(failedDelivery.getId())) {
                        successfulRetries.incrementAndGet();
                    }
                } catch (Throwable throwable) {
                    failures.add(throwable);
                }
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

        NotificationDelivery retriedDelivery = deliveryRepository.findById(failedDelivery.getId()).orElseThrow();
        assertThat(failures).isEmpty();
        assertThat(successfulRetries).hasValue(1);
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

    @Test
    void databaseEnforcesWorldEventSourceEventIdUniqueness() {
        worldEventRepository.saveAndFlush(
            worldEvent("source-event-unique", EventType.BREAKING_NEWS, EventCategory.NEWS, Severity.HIGH)
        );

        assertThatThrownBy(() -> worldEventRepository.saveAndFlush(
            worldEvent("source-event-unique", EventType.MARKET_MOVEMENT, EventCategory.MARKET, Severity.CRITICAL)
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

    private static Alert alert(
        AlertStatus status,
        EventType eventType,
        EventCategory category,
        Severity minimumSeverity,
        ChannelType channelType,
        String target
    ) {
        return Alert.builder()
            .userId(UUID.randomUUID())
            .name("Critical operations")
            .eventType(eventType)
            .category(category)
            .minimumSeverity(minimumSeverity)
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

    private static Stream<Arguments> nonMatchingAlertCases() {
        return Stream.of(
            Arguments.of(
                "severity-below-threshold",
                EventType.BREAKING_NEWS,
                EventCategory.NEWS,
                Severity.HIGH,
                EventType.BREAKING_NEWS,
                EventCategory.NEWS,
                Severity.MEDIUM
            ),
            Arguments.of(
                "category-mismatch",
                EventType.BREAKING_NEWS,
                EventCategory.NEWS,
                Severity.MEDIUM,
                EventType.BREAKING_NEWS,
                EventCategory.SECURITY,
                Severity.HIGH
            ),
            Arguments.of(
                "event-type-mismatch",
                EventType.BREAKING_NEWS,
                EventCategory.NEWS,
                Severity.MEDIUM,
                EventType.MARKET_MOVEMENT,
                EventCategory.NEWS,
                Severity.HIGH
            )
        );
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
        private final ConcurrentLinkedQueue<RuntimeException> exceptions = new ConcurrentLinkedQueue<>();
        private final CopyOnWriteArrayList<Boolean> transactionStates = new CopyOnWriteArrayList<>();
        private volatile CountDownLatch sendStarted;
        private volatile CountDownLatch releaseSend;

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
            awaitIfBlocked();
            RuntimeException exception = exceptions.poll();
            if (exception != null) {
                throw exception;
            }
            NotificationResult result = results.poll();
            return result == null ? NotificationResult.sent() : result;
        }

        void nextResult(NotificationResult result) {
            results.add(result);
        }

        void nextException(RuntimeException exception) {
            exceptions.add(exception);
        }

        void blockNextSend(CountDownLatch sendStarted, CountDownLatch releaseSend) {
            this.sendStarted = sendStarted;
            this.releaseSend = releaseSend;
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
            exceptions.clear();
            transactionStates.clear();
            sendStarted = null;
            releaseSend = null;
        }

        private void awaitIfBlocked() {
            CountDownLatch currentSendStarted = sendStarted;
            CountDownLatch currentReleaseSend = releaseSend;
            if (currentSendStarted == null || currentReleaseSend == null) {
                return;
            }

            currentSendStarted.countDown();
            try {
                if (!currentReleaseSend.await(5, TimeUnit.SECONDS)) {
                    throw new AssertionError("Timed out waiting to release blocked provider send.");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while waiting to release blocked provider send.", exception);
            } finally {
                sendStarted = null;
                releaseSend = null;
            }
        }
    }
}
