package edu.cit.delacruz.campusclinic.service;

import java.math.BigDecimal;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import edu.cit.delacruz.campusclinic.dto.request.BudgetRequest;
import edu.cit.delacruz.campusclinic.dto.response.BudgetResponse;
import edu.cit.delacruz.campusclinic.entity.Budget;
import edu.cit.delacruz.campusclinic.entity.User;
import edu.cit.delacruz.campusclinic.exception.BadRequestException;
import edu.cit.delacruz.campusclinic.exception.ResourceNotFoundException;
import edu.cit.delacruz.campusclinic.repository.BudgetRepository;
import edu.cit.delacruz.campusclinic.repository.ExpenseRepository;
import edu.cit.delacruz.campusclinic.repository.UserRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BudgetService {

    private final BudgetRepository budgetRepository;
    private final UserRepository userRepository;
    private final ExpenseRepository expenseRepository;

    @Transactional
    public BudgetResponse create(Long userId, BudgetRequest request) {
        if (request.getEndDate().isBefore(request.getStartDate())) {
            throw new BadRequestException("End date must be after start date");
        }

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

        // Calculate already-spent amount
        BigDecimal spent = expenseRepository.sumAmountByUserIdAndCategoryAndDateRange(
                userId, request.getCategory(), request.getStartDate(), request.getEndDate());
        budget.setSpentAmount(spent != null ? spent : BigDecimal.ZERO);

        return mapToResponse(budgetRepository.save(budget));
    }

    @Transactional(readOnly = true)
    public Page<BudgetResponse> getAllByUser(Long userId, Pageable pageable) {
        return budgetRepository.findAllByUserId(userId, pageable).map(this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public BudgetResponse getById(Long userId, Long budgetId) {
        Budget budget = budgetRepository.findById(budgetId)
                .orElseThrow(() -> new ResourceNotFoundException("Budget", "id", budgetId));
        validateOwnership(budget, userId);
        return mapToResponse(budget);
    }

    @Transactional
    public BudgetResponse update(Long userId, Long budgetId, BudgetRequest request) {
        Budget budget = budgetRepository.findById(budgetId)
                .orElseThrow(() -> new ResourceNotFoundException("Budget", "id", budgetId));
        validateOwnership(budget, userId);

        if (request.getEndDate().isBefore(request.getStartDate())) {
            throw new BadRequestException("End date must be after start date");
        }

        budget.setCategory(request.getCategory());
        budget.setLimitAmount(request.getLimitAmount());
        budget.setCurrency(request.getCurrency());
        budget.setStartDate(request.getStartDate());
        budget.setEndDate(request.getEndDate());
        budget.setNotes(request.getNotes());

        return mapToResponse(budgetRepository.save(budget));
    }

    @Transactional
    public void delete(Long userId, Long budgetId) {
        Budget budget = budgetRepository.findById(budgetId)
                .orElseThrow(() -> new ResourceNotFoundException("Budget", "id", budgetId));
        validateOwnership(budget, userId);
        budgetRepository.delete(budget);
    }

    private void validateOwnership(Budget budget, Long userId) {
        if (!budget.getUser().getId().equals(userId)) {
            throw new ResourceNotFoundException("Budget", "id", budget.getId());
        }
    }

    public BudgetResponse mapToResponse(Budget budget) {
        BigDecimal remaining = budget.getLimitAmount().subtract(budget.getSpentAmount());
        return BudgetResponse.builder()
                .id(budget.getId())
                .category(budget.getCategory())
                .limitAmount(budget.getLimitAmount())
                .spentAmount(budget.getSpentAmount())
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
}
