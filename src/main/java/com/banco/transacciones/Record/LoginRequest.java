package com.banco.transacciones.Record;

public record LoginRequest(
    String celular,
    String password) {
}