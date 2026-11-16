package com.banco.transacciones.Record;

public record RegistroRequest(
    String nombre,
    String apellido,
    String documento,
    String fechaNacimiento,
    String celular,
    String password) {
}