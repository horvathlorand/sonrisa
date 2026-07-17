package com.example.alerts.event.application;

import com.example.alerts.common.application.TransactionCutter;
import com.example.alerts.event.domain.WorldEvent;
import com.example.alerts.event.infrastructure.WorldEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class WorldEventIngestionService {

    private final TransactionCutter transactionCutter;
    private final WorldEventRepository worldEventRepository;

    public WorldEvent persistIfNew(WorldEvent event) {
        return transactionCutter.inNewTransaction(() ->
            worldEventRepository.findBySourceEventId(event.getSourceEventId())
                .orElseGet(() -> worldEventRepository.saveAndFlush(event))
        );
    }
}
