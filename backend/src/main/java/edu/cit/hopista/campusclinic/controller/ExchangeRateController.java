package edu.cit.hopista.campusclinic.controller;

import java.math.BigDecimal;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import edu.cit.hopista.campusclinic.service.ExchangeRateService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/exchange-rates")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('USER','ADMIN')")
public class ExchangeRateController {

    private final ExchangeRateService exchangeRateService;

    /**
     * GET /api/v1/exchange-rates?base=PHP
     * Returns all exchange rates for the given base currency.
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getRates(
            @RequestParam(defaultValue = "PHP") String base) {
        return ResponseEntity.ok(exchangeRateService.getRates(base));
    }

    /**
     * GET /api/v1/exchange-rates/convert?amount=100&from=PHP&to=USD
     */
    @GetMapping("/convert")
    public ResponseEntity<Map<String, Object>> convert(
            @RequestParam BigDecimal amount,
            @RequestParam String from,
            @RequestParam String to) {
        BigDecimal converted = exchangeRateService.convert(amount, from, to);
        return ResponseEntity.ok(Map.of(
                "from", from.toUpperCase(),
                "to", to.toUpperCase(),
                "originalAmount", amount,
                "convertedAmount", converted
        ));
    }
}
