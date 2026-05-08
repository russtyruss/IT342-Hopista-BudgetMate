package edu.cit.hopista.budgetmate;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class BudgetMateApplication {

    public static void main(String[] args) {
        SpringApplication.run(BudgetMateApplication.class, args);
    }
}
