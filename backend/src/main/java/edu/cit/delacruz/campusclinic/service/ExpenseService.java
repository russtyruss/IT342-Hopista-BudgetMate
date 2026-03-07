package edu.cit.delacruz.campusclinic.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import edu.cit.delacruz.campusclinic.dto.request.ExpenseRequest;
import edu.cit.delacruz.campusclinic.dto.response.ExpenseResponse;
import edu.cit.delacruz.campusclinic.entity.Expense;
import edu.cit.delacruz.campusclinic.entity.User;
import edu.cit.delacruz.campusclinic.exception.ResourceNotFoundException;
import edu.cit.delacruz.campusclinic.repository.ExpenseRepository;
import edu.cit.delacruz.campusclinic.repository.UserRepository;
import edu.cit.delacruz.campusclinic.websocket.DashboardNotificationService;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final UserRepository userRepository;
    private final DashboardNotificationService dashboardNotificationService;

    @Transactional
    public ExpenseResponse create(Long userId, ExpenseRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        Expense expense = Expense.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .category(request.getCategory())
                .expenseDate(request.getExpenseDate())
                .isRecurring(request.getIsRecurring())
                .user(user)
                .build();

        expense = expenseRepository.save(expense);

        // Notify WebSocket dashboard
        dashboardNotificationService.notifyExpenseUpdate(userId);

        return mapToResponse(expense);
    }

    @Transactional(readOnly = true)
    public Page<ExpenseResponse> getAllByUser(Long userId, Pageable pageable) {
        return expenseRepository.findAllByUserId(userId, pageable)
                .map(this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public ExpenseResponse getById(Long userId, Long expenseId) {
        Expense expense = expenseRepository.findById(expenseId)
                .orElseThrow(() -> new ResourceNotFoundException("Expense", "id", expenseId));
        validateOwnership(expense, userId);
        return mapToResponse(expense);
    }

    @Transactional
    public ExpenseResponse update(Long userId, Long expenseId, ExpenseRequest request) {
        Expense expense = expenseRepository.findById(expenseId)
                .orElseThrow(() -> new ResourceNotFoundException("Expense", "id", expenseId));
        validateOwnership(expense, userId);

        expense.setTitle(request.getTitle());
        expense.setDescription(request.getDescription());
        expense.setAmount(request.getAmount());
        expense.setCurrency(request.getCurrency());
        expense.setCategory(request.getCategory());
        expense.setExpenseDate(request.getExpenseDate());
        expense.setIsRecurring(request.getIsRecurring());

        expense = expenseRepository.save(expense);
        dashboardNotificationService.notifyExpenseUpdate(userId);

        return mapToResponse(expense);
    }

    @Transactional
    public void delete(Long userId, Long expenseId) {
        Expense expense = expenseRepository.findById(expenseId)
                .orElseThrow(() -> new ResourceNotFoundException("Expense", "id", expenseId));
        validateOwnership(expense, userId);
        expenseRepository.delete(expense);
        dashboardNotificationService.notifyExpenseUpdate(userId);
    }

    private void validateOwnership(Expense expense, Long userId) {
        if (!expense.getUser().getId().equals(userId)) {
            throw new ResourceNotFoundException("Expense", "id", expense.getId());
        }
    }

    public ExpenseResponse mapToResponse(Expense expense) {
        return ExpenseResponse.builder()
                .id(expense.getId())
                .title(expense.getTitle())
                .description(expense.getDescription())
                .amount(expense.getAmount())
                .currency(expense.getCurrency())
                .category(expense.getCategory())
                .expenseDate(expense.getExpenseDate())
                .isRecurring(expense.getIsRecurring())
                .userId(expense.getUser().getId())
                .createdAt(expense.getCreatedAt())
                .updatedAt(expense.getUpdatedAt())
                .build();
    }
}
