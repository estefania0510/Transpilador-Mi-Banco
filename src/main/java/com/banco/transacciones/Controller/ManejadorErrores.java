package com.banco.transacciones.Controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.banco.transacciones.Service.CobolTimeoutException;

@RestControllerAdvice
public class ManejadorErrores {

    @ExceptionHandler(CobolTimeoutException.class)
    public ResponseEntity<Map<String, Object>> timeoutCobol(CobolTimeoutException e) {
        return ResponseEntity.status(504).body(Map.of(
                "exitoso", false,
                "mensaje", "La operación tardó demasiado y fue cancelada. No se hizo ningún cambio. Intenta de nuevo"));
    }
}