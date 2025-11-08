package com.parser.currency.repository;

import com.parser.currency.model.CurrencyRate;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;

public interface CurrencyRateRepository extends JpaRepository<CurrencyRate, Long> {

    List<CurrencyRate> findByDateAndCharCodeIn(LocalDate date, List<String> charCodes);

    List<CurrencyRate> findTop5ByCharCodeOrderByDateDesc(String charCode);
}