package com.banco.transacciones.Record;

public record MovimientoResponse(
    long id,
    String tipo,
    double monto,
    double gmf,
    double saldoDespues,
    String contraparte,
    String fecha) {
}