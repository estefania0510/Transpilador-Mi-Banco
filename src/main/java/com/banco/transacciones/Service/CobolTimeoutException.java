package com.banco.transacciones.Service;

public class CobolTimeoutException extends RuntimeException {

    public CobolTimeoutException(String mensaje) {
        super(mensaje);
    }
}