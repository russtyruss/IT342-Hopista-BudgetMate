package edu.cit.delacruz.campusclinic.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExchangeRateService {

    private final RestTemplate restTemplate;

    @Value("${app.exchange-rate.api-key}")
    private String apiKey;

    @Value("${app.exchange-rate.api-url:https://v6.exchangerate-api.com/v6}")
    private String apiBaseUrl;

    /**
     * Fetch all exchange rates for the given base currency.
     * Results are cached to avoid excessive API calls.
     */
    @Cacheable(value = "exchangeRates", key = "#baseCurrency")
    @SuppressWarnings("unchecked")
    public Map<String, Object> getRates(String baseCurrency) {
        String url = UriComponentsBuilder
                .fromUriString(apiBaseUrl)
                .pathSegment(apiKey, "latest", baseCurrency.toUpperCase())
                .build().toUriString();

        log.info("Fetching exchange rates for base: {}", baseCurrency);
        Map<String, Object> response = restTemplate.getForObject(url, Map.class);

        if (response == null || !"success".equals(response.get("result"))) {
            log.error("Failed to fetch exchange rates from external API");
            throw new RuntimeException("Unable to fetch exchange rates. Please try again later.");
        }
        return response;
    }

    /**
     * Convert an amount from one currency to another.
     */
    @SuppressWarnings("unchecked")
    public BigDecimal convert(BigDecimal amount, String fromCurrency, String toCurrency) {
        if (fromCurrency.equalsIgnoreCase(toCurrency)) {
            return amount;
        }

        Map<String, Object> data = getRates(fromCurrency.toUpperCase());
        Map<String, Double> conversionRates =
                (Map<String, Double>) data.get("conversion_rates");

        Double rate = conversionRates.get(toCurrency.toUpperCase());
        if (rate == null) {
            throw new IllegalArgumentException("Unsupported target currency: " + toCurrency);
        }

        return amount.multiply(BigDecimal.valueOf(rate)).setScale(2, RoundingMode.HALF_UP);
    }
}
