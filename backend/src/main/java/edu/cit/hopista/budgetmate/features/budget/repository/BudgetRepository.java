package edu.cit.hopista.budgetmate.features.budget.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import edu.cit.hopista.budgetmate.features.budget.entity.Budget;

@Repository
public interface BudgetRepository extends JpaRepository<Budget, Long> {

        Optional<Budget> findByIdAndUserId(Long id, Long userId);

        void deleteAllByUserId(Long userId);

    Page<Budget> findAllByUserId(Long userId, Pageable pageable);

    List<Budget> findAllByUserIdAndCategory(Long userId, String category);

    Optional<Budget> findByUserIdAndCategoryAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
            Long userId,
            String category,
            LocalDate startDate,
            LocalDate endDate);

    List<Budget> findAllByUserIdAndStartDateGreaterThanEqualAndEndDateLessThanEqual(
            Long userId,
            LocalDate start,
            LocalDate end);
}
