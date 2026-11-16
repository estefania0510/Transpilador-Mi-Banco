package com.banco.transacciones.Record;

public record ConsignacionRequest(
    String cuenta,
    double monto) {
}