package com.banco.transacciones.Record;

public record RegistroResponse(
    boolean exitoso,
    String mensaje) {
}