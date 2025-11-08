package com.parser.currency.controller;

import com.parser.currency.model.CurrencyRate;
import com.parser.currency.service.CbrService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
public class CurrencyController {

    @Autowired
    private CbrService cbrService;

    /**
     * POST http://localhost:8080/start-parsing
     */
    @PostMapping("/start-parsing")
    public ResponseEntity<String> startParsingManually() {
        cbrService.parseAndSave();
        return ResponseEntity.ok("Парсинг XML с ЦБ РФ запущен в кастомном ExecutorService. Проверьте логи.");
    }

    /**
     * GET http://localhost:8080/answer?sortBy=rate
     */
    @GetMapping("/answer")
    public List<CurrencyRate> getAnswer(
            @RequestParam(required = false, defaultValue = "date") String sortBy) {

        return cbrService.getAllSortedRates(sortBy);
    }

    /**
     * GET http://localhost:8080/answer/search?date=YYYY-MM-DD&currencies=USD,EUR,CNY
     */
    @GetMapping("/answer/search")
    public List<CurrencyRate> getRatesByDateAndCurrency(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam List<String> currencies) {

        return cbrService.getRatesByDateAndCodes(date, currencies);
    }

    @GetMapping("/convert")
    public String convertCurrency(
            @RequestParam double amount,
            @RequestParam String to) {

        List<CurrencyRate> rates = cbrService.getAllSortedRates("date");
        CurrencyRate targetRate = rates.stream()
                .filter(rate -> rate.getCharCode().equals(to))
                .findFirst()
                .orElse(null);

        if (targetRate == null) {
            return "Курс для валюты " + to + " не найден";
        }

        double convertedAmount = amount / targetRate.getRate();
        return String.format("%.2f RUB = %.2f %s (курс: 1 %s = %.4f RUB)",
                amount, convertedAmount, to, to, targetRate.getRate());
    }
}