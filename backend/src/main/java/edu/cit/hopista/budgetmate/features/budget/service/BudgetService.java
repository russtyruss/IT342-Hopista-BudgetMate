package edu.cit.hopista.budgetmate.features.budget.service;

import java.math.BigDecimal;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import edu.cit.hopista.budgetmate.features.budget.dto.request.BudgetRequest;
import edu.cit.hopista.budgetmate.features.budget.dto.response.BudgetResponse;
import edu.cit.hopista.budgetmate.features.budget.entity.Budget;
import edu.cit.hopista.budgetmate.features.user.entity.User;
import edu.cit.hopista.budgetmate.shared.exception.BadRequestException;
import edu.cit.hopista.budgetmate.shared.exception.ResourceNotFoundException;
import edu.cit.hopista.budgetmate.features.budget.repository.BudgetRepository;
import edu.cit.hopista.budgetmate.features.expense.repository.ExpenseRepository;
import edu.cit.hopista.budgetmate.features.user.repository.UserRepository;
import edu.cit.hopista.budgetmate.shared.websocket.DashboardNotificationService;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BudgetService {

    private final BudgetRepository budgetRepository;
    private final UserRepository userRepository;
    private final ExpenseRepository expenseRepository;
    private final DashboardNotificationService dashboardNotificationService;

    @Transactional
    public BudgetResponse create(Long userId, BudgetRequest request) {
        validateScheduleDates(request);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        Budget budget = Budget.builder()
                .category(request.getCategory())
                .limitAmount(request.getLimitAmount())
                .currency(request.getCurrency())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .notes(request.getNotes())
                .user(user)
                .build();

        // New budgets should not auto-link existing expenses by category.
        budget.setSpentAmount(BigDecimal.ZERO);

        BudgetResponse response = mapToResponseWithLinkedSpent(budgetRepository.save(budget));
        dashboardNotificationService.notifyBudgetUpdate(userId);
        return response;
    }

    @Transactional(readOnly = true)
    public Page<BudgetResponse> getAllByUser(Long userId, Pageable pageable) {
        return budgetRepository.findAllByUserId(userId, pageable)
                .map(this::mapToResponseWithLinkedSpent);
    }

    @Transactional(readOnly = true)
    public BudgetResponse getById(Long userId, Long budgetId) {
        Budget budget = budgetRepository.findById(budgetId)
                .orElseThrow(() -> new ResourceNotFoundException("Budget", "id", budgetId));
        validateOwnership(budget, userId);
        return mapToResponseWithLinkedSpent(budget);
    }

    @Transactional
    public BudgetResponse update(Long userId, Long budgetId, BudgetRequest request) {
        Budget budget = budgetRepository.findById(budgetId)
                .orElseThrow(() -> new ResourceNotFoundException("Budget", "id", budgetId));
        validateOwnership(budget, userId);

        validateScheduleDates(request);

        budget.setCategory(request.getCategory());
        budget.setLimitAmount(request.getLimitAmount());
        budget.setCurrency(request.getCurrency());
        budget.setStartDate(request.getStartDate());
        budget.setEndDate(request.getEndDate());
        budget.setNotes(request.getNotes());

        // Recompute using explicit budget links only.
        BigDecimal spent = expenseRepository.sumAmountByUserIdAndBudgetId(userId, budgetId);
        budget.setSpentAmount(spent != null ? spent : BigDecimal.ZERO);

        BudgetResponse response = mapToResponseWithLinkedSpent(budgetRepository.save(budget));
        dashboardNotificationService.notifyBudgetUpdate(userId);
        return response;
    }

    private void validateScheduleDates(BudgetRequest request) {
        boolean hasStart = request.getStartDate() != null;
        boolean hasEnd = request.getEndDate() != null;

        if (hasStart != hasEnd) {
            throw new BadRequestException("Start date and end date must both be provided for scheduled budgets");
        }

        if (hasStart && request.getEndDate().isBefore(request.getStartDate())) {
            throw new BadRequestException("End date must be after start date");
        }
    }

    private boolean hasSchedule(BudgetRequest request) {
        return request.getStartDate() != null && request.getEndDate() != null;
    }

    @Transactional
    public void delete(Long userId, Long budgetId) {
        Budget budget = budgetRepository.findById(budgetId)
                .orElseThrow(() -> new ResourceNotFoundException("Budget", "id", budgetId));
        validateOwnership(budget, userId);

        long deletedLinkedExpenses = expenseRepository.deleteAllByUserIdAndBudgetId(userId, budgetId);
        budgetRepository.delete(budget);

        if (deletedLinkedExpenses > 0) {
            dashboardNotificationService.notifyExpenseUpdate(userId);
        }
        dashboardNotificationService.notifyBudgetUpdate(userId);
    }

    private void validateOwnership(Budget budget, Long userId) {
        if (!budget.getUser().getId().equals(userId)) {
            throw new ResourceNotFoundException("Budget", "id", budget.getId());
        }
    }

    public BudgetResponse mapToResponse(Budget budget) {
        BigDecimal limitAmount = budget.getLimitAmount() != null ? budget.getLimitAmount() : BigDecimal.ZERO;
        BigDecimal spentAmount = budget.getSpentAmount() != null ? budget.getSpentAmount() : BigDecimal.ZERO;
        BigDecimal remaining = limitAmount.subtract(spentAmount);
        return BudgetResponse.builder()
                .id(budget.getId())
                .category(budget.getCategory())
                .limitAmount(limitAmount)
                .spentAmount(spentAmount)
                .remainingAmount(remaining)
                .currency(budget.getCurrency())
                .startDate(budget.getStartDate())
                .endDate(budget.getEndDate())
                .notes(budget.getNotes())
                .userId(budget.getUser().getId())
                .createdAt(budget.getCreatedAt())
                .updatedAt(budget.getUpdatedAt())
                .build();
    }

    private BudgetResponse mapToResponseWithLinkedSpent(Budget budget) {
        BigDecimal linkedSpent = expenseRepository.sumAmountByUserIdAndBudgetId(
                budget.getUser().getId(),
                budget.getId()
        );
        budget.setSpentAmount(linkedSpent != null ? linkedSpent : BigDecimal.ZERO);
        return mapToResponse(budget);
    }
}
