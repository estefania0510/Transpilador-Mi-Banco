package com.banco.transacciones.Record;

public record TransferenciaResponse(
    boolean exitoso,
    String mensaje,
    double saldoOrigen,
    double saldoDestino) {
}