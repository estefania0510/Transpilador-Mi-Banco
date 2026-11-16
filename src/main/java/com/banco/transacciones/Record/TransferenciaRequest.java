package com.banco.transacciones.Record;

public record TransferenciaRequest(
    String cuentaOrigen,
    String cuentaDestino,
    double monto) {
}