package com.banco.transacciones.Record;

public record ConsignacionResponse(
    boolean exitoso,
    String mensaje,
    double saldo) {
}