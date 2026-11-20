package com.banco.transacciones.Record;

public record PerfilResponse(
    String nombre,
    String apellido,
    String documento,
    String celular,
    String fechaNacimiento
) {}