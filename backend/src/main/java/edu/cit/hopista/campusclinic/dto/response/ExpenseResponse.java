package edu.cit.hopista.campusclinic.dto.response;

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
public class ExpenseResponse {

    private Long id;
    private String title;
    private String description;
    private BigDecimal amount;
    private String currency;
    private String category;
    private LocalDate expenseDate;
    private Boolean isRecurring;
    private Long budgetId;
    private Long userId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
