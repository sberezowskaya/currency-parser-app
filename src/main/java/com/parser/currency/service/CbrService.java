package com.parser.currency.service;

import com.parser.currency.model.CurrencyRate;
import com.parser.currency.model.ValCurs;
import com.parser.currency.model.Valute;
import com.parser.currency.repository.CurrencyRateRepository;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

@Service
@Slf4j
public class CbrService {

    private final String CBR_URL = "https://www.cbr.ru/scripts/XML_daily.asp";
    private final DateTimeFormatter cbrDateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private final XmlMapper xmlMapper;

    private final CurrencyRateRepository repository;
    private final ExecutorService customFixedThreadPool;
    private final WebClient webClient;

    @Autowired
    public CbrService(WebClient webClient,
                      CurrencyRateRepository repository,
                      ExecutorService customFixedThreadPool) {
        this.repository = repository;
        this.customFixedThreadPool = customFixedThreadPool;
        this.webClient = webClient;

        this.xmlMapper = new XmlMapper();
        this.xmlMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public ValCurs fetchXmlData() {
        log.info("Запрос XML данных с ЦБ РФ через WebClient.");

        try {
            String xmlResponse = webClient.get()
                    .uri(CBR_URL)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            if (xmlResponse == null || xmlResponse.isEmpty()) {
                log.error("Получен пустой ответ от ЦБ РФ");
                return null;
            }

            log.info("Получен XML ответ длиной {} символов", xmlResponse.length());

            return parseXmlWithJackson(xmlResponse);

        } catch (WebClientResponseException e) {
            log.error("Ошибка HTTP при запросе к ЦБ РФ: {} - {}", e.getStatusCode(), e.getStatusText());
            return null;
        } catch (Exception e) {
            log.error("Неожиданная ошибка при запросе к ЦБ РФ: {}", e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    private ValCurs parseXmlWithJackson(String xmlResponse) {
        try {
            log.info("Начинаем парсинг XML с Jackson...");

            ValCurs valCurs = xmlMapper.readValue(xmlResponse, ValCurs.class);

            log.info("Успешно распарсены данные за дату: {}", valCurs.getDate());
            if (valCurs.getValutes() != null) {
                log.info("Найдено {} валют", valCurs.getValutes().size());

                valCurs.getValutes().stream()
                        .limit(3)
                        .forEach(v -> log.info("Валюта: {} - {} = {}", v.getCharCode(), v.getName(), v.getValue()));
            } else {
                log.warn("Список валют пуст");
            }

            return valCurs;

        } catch (Exception e) {
            log.error("Ошибка парсинга XML Jackson: {}", e.getMessage());
            e.printStackTrace();
            if (xmlResponse != null && xmlResponse.length() > 200) {
                log.debug("Начало XML: {}", xmlResponse.substring(0, 200));
            }
            return null;
        }
    }

    @Scheduled(fixedRate = 30 * 60 * 1000) // 30 минут в миллисекундах
    public void scheduledParseAndSave() {
        log.info("=== [SCHEDULER] Начало планового обновления курсов. ===");
        parseAndSave();
    }

    public void parseAndSave() {
        ValCurs valCurs = fetchXmlData();

        if (valCurs == null || valCurs.getValutes() == null || valCurs.getValutes().isEmpty()) {
            log.error("Не удалось получить данные или список валют пуст.");
            return;
        }

        LocalDate rateDate;
        try {
            rateDate = LocalDate.parse(valCurs.getDate(), cbrDateFormatter);
            log.info("Дата курсов: {}", rateDate);
        } catch (Exception e) {
            log.error("Ошибка парсинга даты: {}", valCurs.getDate(), e);
            rateDate = LocalDate.now();
        }

        List<Valute> valutes = valCurs.getValutes();
        log.info("Найдено {} валют для обработки", valutes.size());

        final LocalDate finalRateDate = rateDate;

        for (Valute valute : valutes) {
            final Valute currentValute = valute;
            customFixedThreadPool.submit(() -> saveValute(currentValute, finalRateDate));
        }

        log.info("=== Все {} валют отправлены на обработку в ExecutorService. ===", valutes.size());
    }

    private void saveValute(Valute valute, LocalDate date) {
        try {
            log.info("Сохранение валюты: {}", valute.getCharCode());

            String valueStr = valute.getValue().replace(',', '.');

            double rateValue = Double.parseDouble(valueStr) / valute.getNominal();

            CurrencyRate rate = new CurrencyRate();
            rate.setCharCode(valute.getCharCode());
            rate.setName(valute.getName());
            rate.setRate(rateValue);
            rate.setDate(date);

            CurrencyRate saved = repository.save(rate);
            log.info("✅ УСПЕШНО сохранен курс для {}: {} (номинал: {})",
                    valute.getCharCode(), rateValue, valute.getNominal());

        } catch (NumberFormatException e) {
            log.error("❌ Ошибка парсинга курса для {}: значение '{}'",
                    valute.getCharCode(), valute.getValue());
        } catch (Exception e) {
            log.error("❌ Ошибка при сохранении курса для {}: {}",
                    valute.getCharCode(), e.getMessage());
            e.printStackTrace();
        }
    }

    public List<CurrencyRate> getAllSortedRates(String sortBy) {
        List<CurrencyRate> allRates = repository.findAll();
        log.info("Загружено {} записей из БД для сортировки", allRates.size());

        return allRates.parallelStream()
                .sorted(getComparator(sortBy))
                .collect(Collectors.toList());
    }

    private Comparator<CurrencyRate> getComparator(String sortBy) {
        return switch (sortBy.toLowerCase()) {
            case "rate" -> Comparator.comparing(CurrencyRate::getRate).reversed();
            case "name" -> Comparator.comparing(CurrencyRate::getName);
            case "charcode" -> Comparator.comparing(CurrencyRate::getCharCode);
            default -> Comparator.comparing(CurrencyRate::getDate).reversed();
        };
    }

    public List<CurrencyRate> getRatesByDateAndCodes(LocalDate date, List<String> codes) {
        log.info("Поиск курсов за дату {} для валют: {}", date, codes);
        List<CurrencyRate> rates = repository.findByDateAndCharCodeIn(date, codes);
        log.info("Найдено {} записей", rates.size());
        return rates;
    }

    public List<CurrencyRate> getLastRatesForCurrency(String charCode) {
        return repository.findTop5ByCharCodeOrderByDateDesc(charCode);
    }

    public List<LocalDate> getAvailableDates() {
        return repository.findAll().stream()
                .map(CurrencyRate::getDate)
                .distinct()
                .sorted(Comparator.reverseOrder())
                .collect(Collectors.toList());
    }
}