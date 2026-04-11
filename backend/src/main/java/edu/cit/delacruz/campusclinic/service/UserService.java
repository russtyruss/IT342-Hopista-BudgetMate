package edu.cit.delacruz.campusclinic.service;

import java.time.LocalDateTime;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import edu.cit.delacruz.campusclinic.dto.response.UserResponse;
import edu.cit.delacruz.campusclinic.entity.User;
import edu.cit.delacruz.campusclinic.exception.BadRequestException;
import edu.cit.delacruz.campusclinic.exception.ResourceNotFoundException;
import edu.cit.delacruz.campusclinic.repository.BudgetRepository;
import edu.cit.delacruz.campusclinic.repository.ExpenseRepository;
import edu.cit.delacruz.campusclinic.repository.UserRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final ExpenseRepository expenseRepository;
    private final BudgetRepository budgetRepository;

    @Transactional(readOnly = true)
    public UserResponse getUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
        return mapToResponse(user);
    }

    @Transactional(readOnly = true)
    public Page<UserResponse> getAllUsers(Pageable pageable) {
        return userRepository.findAll(pageable).map(this::mapToResponse);
    }

    @Transactional
    public void deleteUser(Long id, Long requestedByUserId) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));

        if (user.getId().equals(requestedByUserId)) {
            throw new BadRequestException("Admin cannot delete their own account.");
        }

        if (user.getRoles().stream().anyMatch(role -> role.name().equals("ADMIN"))) {
            throw new BadRequestException("Admin accounts cannot be deleted.");
        }

        expenseRepository.deleteAllByUserId(id);
        budgetRepository.deleteAllByUserId(id);
        userRepository.delete(user);
    }

    public UserResponse mapToResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .imageUrl(user.getImageUrl())
                .emailVerified(user.getEmailVerified())
                .enabled(user.getEnabled())
                .status(resolveStatus(user))
                .provider(user.getProvider().name())
                .roles(user.getRoles().stream().map(Enum::name).collect(Collectors.toSet()))
                .createdAt(user.getCreatedAt())
                .lastLoginAt(user.getLastLoginAt())
                .build();
    }

    private String resolveStatus(User user) {
        if (!Boolean.TRUE.equals(user.getEnabled())) {
            return "INACTIVE";
        }

        LocalDateTime lastLogin = user.getLastLoginAt();
        if (lastLogin == null) {
            return "INACTIVE";
        }

        return lastLogin.isAfter(LocalDateTime.now().minusDays(30)) ? "ACTIVE" : "INACTIVE";
    }
}
