package edu.cit.hopista.campusclinic.shared.websocket;

import java.time.LocalDateTime;
import java.util.Map;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminUsersNotificationService {

    private final SimpMessagingTemplate messagingTemplate;

    public void notifyUsersUpdate(Long affectedUserId) {
        String destination = "/topic/admin/users";
        Map<String, Object> payload = Map.of(
                "type", "ADMIN_USERS_UPDATE",
                "affectedUserId", affectedUserId,
                "timestamp", LocalDateTime.now().toString()
        );
        messagingTemplate.convertAndSend(destination, payload);
        log.debug("Sent ADMIN_USERS_UPDATE notification to {}", destination);
    }
}