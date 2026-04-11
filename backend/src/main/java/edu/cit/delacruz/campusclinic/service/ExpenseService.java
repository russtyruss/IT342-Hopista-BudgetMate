package edu.cit.delacruz.campusclinic.service;

import java.math.BigDecimal;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import edu.cit.delacruz.campusclinic.dto.request.ExpenseRequest;
import edu.cit.delacruz.campusclinic.dto.response.ExpenseResponse;
import edu.cit.delacruz.campusclinic.entity.Budget;
import edu.cit.delacruz.campusclinic.entity.Expense;
import edu.cit.delacruz.campusclinic.entity.User;
import edu.cit.delacruz.campusclinic.exception.BadRequestException;
import edu.cit.delacruz.campusclinic.exception.ResourceNotFoundException;
import edu.cit.delacruz.campusclinic.repository.BudgetRepository;
import edu.cit.delacruz.campusclinic.repository.ExpenseRepository;
import edu.cit.delacruz.campusclinic.repository.UserRepository;
import edu.cit.delacruz.campusclinic.websocket.DashboardNotificationService;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final BudgetRepository budgetRepository;
    private final UserRepository userRepository;
    private final DashboardNotificationService dashboardNotificationService;

    @Transactional
    public ExpenseResponse create(Long userId, ExpenseRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        Budget linkedBudget = getLinkedBudget(userId, request.getBudgetId(), request.getExpenseDate());

        Expense expense = Expense.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .category(request.getCategory())
                .expenseDate(request.getExpenseDate())
                .isRecurring(request.getIsRecurring())
                .budget(linkedBudget)
                .user(user)
                .build();

        expense = expenseRepository.save(expense);
            if (linkedBudget != null) {
                adjustBudgetSpent(linkedBudget, request.getAmount());
                dashboardNotificationService.notifyBudgetUpdate(userId);
            }

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

        Budget oldBudget = expense.getBudget();
        BigDecimal oldAmount = expense.getAmount();
        Budget newBudget = getLinkedBudget(userId, request.getBudgetId(), request.getExpenseDate());

        expense.setTitle(request.getTitle());
        expense.setDescription(request.getDescription());
        expense.setAmount(request.getAmount());
        expense.setCurrency(request.getCurrency());
        expense.setCategory(request.getCategory());
        expense.setExpenseDate(request.getExpenseDate());
        expense.setIsRecurring(request.getIsRecurring());
        expense.setBudget(newBudget);

        expense = expenseRepository.save(expense);

        if (oldBudget != null && newBudget != null && oldBudget.getId().equals(newBudget.getId())) {
            adjustBudgetSpent(newBudget, request.getAmount().subtract(oldAmount));
            dashboardNotificationService.notifyBudgetUpdate(userId);
        } else {
            if (oldBudget != null) {
                adjustBudgetSpent(oldBudget, oldAmount.negate());
            }
            if (newBudget != null) {
                adjustBudgetSpent(newBudget, request.getAmount());
            }
            if (oldBudget != null || newBudget != null) {
                dashboardNotificationService.notifyBudgetUpdate(userId);
            }
        }

        dashboardNotificationService.notifyExpenseUpdate(userId);

        return mapToResponse(expense);
    }

    @Transactional
    public void delete(Long userId, Long expenseId) {
        Expense expense = expenseRepository.findById(expenseId)
                .orElseThrow(() -> new ResourceNotFoundException("Expense", "id", expenseId));
        validateOwnership(expense, userId);

        if (expense.getBudget() != null) {
            adjustBudgetSpent(expense.getBudget(), expense.getAmount().negate());
            dashboardNotificationService.notifyBudgetUpdate(userId);
        }

        expenseRepository.delete(expense);
        dashboardNotificationService.notifyExpenseUpdate(userId);
    }

    private Budget getLinkedBudget(Long userId, Long budgetId, java.time.LocalDate expenseDate) {
        if (budgetId == null) {
            return null;
        }

        Budget budget = budgetRepository.findByIdAndUserId(budgetId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Budget", "id", budgetId));

        if (expenseDate.isBefore(budget.getStartDate()) || expenseDate.isAfter(budget.getEndDate())) {
            throw new BadRequestException("Expense date must fall within the selected budget period");
        }

        return budget;
    }

    private void adjustBudgetSpent(Budget budget, BigDecimal delta) {
        BigDecimal current = budget.getSpentAmount() != null ? budget.getSpentAmount() : BigDecimal.ZERO;
        BigDecimal updated = current.add(delta);
        if (updated.compareTo(BigDecimal.ZERO) < 0) {
            updated = BigDecimal.ZERO;
        }
        budget.setSpentAmount(updated);
        budgetRepository.save(budget);
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
                .budgetId(expense.getBudget() != null ? expense.getBudget().getId() : null)
                .userId(expense.getUser().getId())
                .createdAt(expense.getCreatedAt())
                .updatedAt(expense.getUpdatedAt())
                .build();
    }
}
