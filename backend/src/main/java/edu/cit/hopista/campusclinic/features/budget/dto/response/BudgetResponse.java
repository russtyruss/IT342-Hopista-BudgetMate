package edu.cit.hopista.campusclinic.features.budget.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BudgetResponse {

    private Long id;
    private String category;
    private BigDecimal limitAmount;
    private BigDecimal spentAmount;
    private BigDecimal remainingAmount;
    private String currency;
    private LocalDate startDate;
    private LocalDate endDate;
    private String notes;
    private Long userId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
