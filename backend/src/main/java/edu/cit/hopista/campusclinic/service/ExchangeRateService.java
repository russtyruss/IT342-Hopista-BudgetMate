package edu.cit.hopista.campusclinic.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
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
        String normalizedBase = baseCurrency.toUpperCase();

        // Avoid blowing up the app if API key is not configured yet in local/dev setups.
        if (isApiKeyMissingOrPlaceholder(apiKey)) {
            log.warn("Exchange rate API key is missing or placeholder. Returning fallback rates.");
            return buildFallbackRates(normalizedBase);
        }

        String url = UriComponentsBuilder
                .fromUriString(apiBaseUrl)
                .pathSegment(apiKey, "latest", normalizedBase)
                .build().toUriString();

        log.info("Fetching exchange rates for base: {}", normalizedBase);
        Map<String, Object> response;
        try {
            response = restTemplate.getForObject(url, Map.class);
        } catch (HttpClientErrorException ex) {
            log.warn("Exchange rate API returned {}. Falling back to base-only rates.", ex.getStatusCode());
            return buildFallbackRates(normalizedBase);
        }

        if (response == null || !"success".equals(response.get("result"))) {
            log.warn("External API response not successful. Falling back to base-only rates.");
            return buildFallbackRates(normalizedBase);
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

    private boolean isApiKeyMissingOrPlaceholder(String key) {
        if (key == null || key.isBlank()) {
            return true;
        }
        return key.contains("<") || key.contains(">") || key.toLowerCase().contains("exchange-rate-api-key");
    }

    private Map<String, Object> buildFallbackRates(String baseCurrency) {
        Map<String, Double> conversionRates = new HashMap<>();
        conversionRates.put(baseCurrency, 1.0d);

        Map<String, Object> response = new HashMap<>();
        response.put("result", "success");
        response.put("base_code", baseCurrency);
        response.put("conversion_rates", conversionRates);
        return response;
    }
}
