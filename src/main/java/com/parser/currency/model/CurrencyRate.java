package com.parser.currency.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Data;

import java.time.LocalDate;

@Entity
@Data
public class CurrencyRate {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String charCode;        // Название валюты
    private String name;            // Полное название
    private double rate;            // Курс к рублю
    private LocalDate date;         // Дата сбора курса
}