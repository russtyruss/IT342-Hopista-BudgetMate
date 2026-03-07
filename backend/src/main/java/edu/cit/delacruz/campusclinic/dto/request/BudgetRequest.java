package edu.cit.delacruz.campusclinic.dto.request;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class BudgetRequest {

    @NotBlank(message = "Category is required")
    @Size(max = 100, message = "Category must not exceed 100 characters")
    private String category;

    @NotNull(message = "Limit amount is required")
    @DecimalMin(value = "0.01", message = "Limit amount must be greater than zero")
    @Digits(integer = 13, fraction = 2, message = "Limit amount format is invalid")
    private BigDecimal limitAmount;

    @NotBlank(message = "Currency is required")
    @Size(min = 3, max = 10)
    private String currency;

    @NotNull(message = "Start date is required")
    private LocalDate startDate;

    @NotNull(message = "End date is required")
    private LocalDate endDate;

    @Size(max = 200, message = "Notes must not exceed 200 characters")
    private String notes;
}
