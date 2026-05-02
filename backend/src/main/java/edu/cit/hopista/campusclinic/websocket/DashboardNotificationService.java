package edu.cit.hopista.campusclinic.websocket;

import java.time.LocalDateTime;
import java.util.Map;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardNotificationService {

    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Notify the user's WebSocket channel that expense data changed.
     */
    public void notifyExpenseUpdate(Long userId) {
        String destination = "/topic/dashboard/" + userId;
        Map<String, Object> payload = Map.of(
                "type", "EXPENSE_UPDATE",
                "userId", userId,
                "timestamp", LocalDateTime.now().toString()
        );
        messagingTemplate.convertAndSend(destination, payload);
        log.debug("Sent EXPENSE_UPDATE notification to {}", destination);
    }

    /**
     * Notify the user's WebSocket channel that budget data changed.
     */
    public void notifyBudgetUpdate(Long userId) {
        String destination = "/topic/dashboard/" + userId;
        Map<String, Object> payload = Map.of(
                "type", "BUDGET_UPDATE",
                "userId", userId,
                "timestamp", LocalDateTime.now().toString()
        );
        messagingTemplate.convertAndSend(destination, payload);
        log.debug("Sent BUDGET_UPDATE notification to {}", destination);
    }
}
