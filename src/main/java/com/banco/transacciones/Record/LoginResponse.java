package com.banco.transacciones.Record;

public record LoginResponse(
    boolean exitoso,
    String mensaje,
    String token,
    String nombre) {
}