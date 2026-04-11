package edu.cit.delacruz.campusclinic.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import edu.cit.delacruz.campusclinic.entity.Expense;

@Repository
public interface ExpenseRepository extends JpaRepository<Expense, Long> {

       void deleteAllByUserId(Long userId);

    Page<Expense> findAllByUserId(Long userId, Pageable pageable);

    List<Expense> findAllByUserIdAndCategory(Long userId, String category);

    List<Expense> findAllByUserIdAndExpenseDateBetween(Long userId, LocalDate start, LocalDate end);

    @Query("SELECT SUM(e.amount) FROM Expense e WHERE e.user.id = :userId AND e.category = :category " +
           "AND e.expenseDate BETWEEN :start AND :end")
    BigDecimal sumAmountByUserIdAndCategoryAndDateRange(
            @Param("userId") Long userId,
            @Param("category") String category,
            @Param("start") LocalDate start,
            @Param("end") LocalDate end);

    @Query("SELECT SUM(e.amount) FROM Expense e WHERE e.user.id = :userId " +
           "AND e.expenseDate BETWEEN :start AND :end")
    BigDecimal sumAmountByUserIdAndDateRange(
            @Param("userId") Long userId,
            @Param("start") LocalDate start,
            @Param("end") LocalDate end);
}
