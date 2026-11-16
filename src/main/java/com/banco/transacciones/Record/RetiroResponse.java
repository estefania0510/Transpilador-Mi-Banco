package com.banco.transacciones.Record;

public record RetiroResponse(
    boolean exitoso, 
    String mensaje, 
    double saldo) {
}