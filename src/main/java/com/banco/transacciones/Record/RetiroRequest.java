package com.banco.transacciones.Record;

public record RetiroRequest(
    String cuenta,
    double monto) {
}