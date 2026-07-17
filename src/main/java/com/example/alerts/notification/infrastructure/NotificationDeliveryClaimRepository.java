package com.example.alerts.notification.infrastructure;

import com.example.alerts.alert.domain.ChannelType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class NotificationDeliveryClaimRepository {

    private final JdbcTemplate jdbcTemplate;

    public Optional<UUID> createClaim(UUID alertId, UUID eventId, ChannelType channelType, String target) {
        UUID deliveryId = UUID.randomUUID();
        String sql = """
            INSERT INTO notification_deliveries (
                id,
                alert_id,
                event_id,
                channel_type,
                target,
                status,
                claimed_at
            )
            VALUES (?, ?, ?, ?, ?, 'PENDING', NOW())
            ON CONFLICT (alert_id, event_id, channel_type, target) DO NOTHING
            RETURNING id
            """;

        List<UUID> ids = jdbcTemplate.query(
            sql,
            ps -> {
                ps.setObject(1, deliveryId);
                ps.setObject(2, alertId);
                ps.setObject(3, eventId);
                ps.setString(4, channelType.name());
                ps.setString(5, target);
            },
            (rs, rowNum) -> rs.getObject("id", UUID.class)
        );
        return ids.stream().findFirst();
    }
}
