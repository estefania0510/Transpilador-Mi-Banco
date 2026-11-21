package com.banco.transacciones.Controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import com.banco.transacciones.Record.ConsignacionRequest;
import com.banco.transacciones.Record.ConsignacionResponse;
import com.banco.transacciones.Record.RetiroRequest;
import com.banco.transacciones.Record.RetiroResponse;
import com.banco.transacciones.Record.TransferenciaRequest;
import com.banco.transacciones.Record.TransferenciaResponse;
import com.banco.transacciones.Service.AuthService;
import com.banco.transacciones.Service.CuentaService;

@RestController
@RequestMapping("/transacciones")
@CrossOrigin(origins = "*")
public class CuentaController {

    private static final String SIN_SESION = "Sesión no válida. Inicia sesión de nuevo";

    private final CuentaService cuentaService;
    private final AuthService authService;

    public CuentaController(CuentaService cuentaService, AuthService authService) {
        this.cuentaService = cuentaService;
        this.authService = authService;
    }

    @GetMapping("/saldo")
    public ResponseEntity<Map<String, Object>> saldo(
            @RequestHeader(value = "Authorization", required = false) String auth) {
        String cuenta = authService.celularDeEncabezado(auth);
        if (cuenta == null) {
            return ResponseEntity.status(401).body(Map.of("mensaje", SIN_SESION));
        }
        if (!cuentaService.existeCuenta(cuenta)) {
            return ResponseEntity.status(404).body(Map.of("mensaje", "La cuenta no existe"));
        }
        return ResponseEntity.ok(Map.of("cuenta", cuenta, "saldo", cuentaService.consultarSaldo(cuenta)));
    }

    @PutMapping("/retirar")
    public ResponseEntity<RetiroResponse> retirar(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @RequestBody Map<String, Object> request) {

        String cuenta = authService.celularDeEncabezado(auth);

        if (cuenta == null) {
            return ResponseEntity.status(401)
                    .body(new RetiroResponse(false, SIN_SESION, 0));
        }

        // Obtener el PIN y el monto enviados desde el frontend
        String pin = request.get("pin") != null
                ? request.get("pin").toString()
                : null;

        double monto;

        try {
            monto = Double.parseDouble(request.get("monto").toString());
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(new RetiroResponse(false, "El monto no es válido", 0));
        }

        // Verificar el PIN antes de realizar el retiro
        if (!authService.validarPin(cuenta, pin)) {
            return ResponseEntity.badRequest()
                    .body(new RetiroResponse(false, "PIN incorrecto", 0));
        }

        RetiroResponse respuesta = cuentaService.retirar(
                new RetiroRequest(cuenta, monto));

        return respuesta.exitoso()
                ? ResponseEntity.ok(respuesta)
                : ResponseEntity.badRequest().body(respuesta);
    }

    @PutMapping("/consignar")
    public ResponseEntity<ConsignacionResponse> consignar(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @RequestBody ConsignacionRequest request) {
        String cuenta = authService.celularDeEncabezado(auth);
        if (cuenta == null) {
            return ResponseEntity.status(401).body(new ConsignacionResponse(false, SIN_SESION, 0));
        }
        ConsignacionResponse respuesta = cuentaService.consignar(new ConsignacionRequest(cuenta, request.monto()));
        return respuesta.exitoso()
                ? ResponseEntity.ok(respuesta)
                : ResponseEntity.badRequest().body(respuesta);
    }

    @PostMapping("/transferir")
    public ResponseEntity<TransferenciaResponse> transferir(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @RequestBody Map<String, Object> request) {

        String cuenta = authService.celularDeEncabezado(auth);

        if (cuenta == null) {
            return ResponseEntity.status(401)
                    .body(new TransferenciaResponse(false, SIN_SESION, 0, 0));
        }

        // Obtener los datos de la transferencia
        String pin = request.get("pin") != null
                ? request.get("pin").toString()
                : null;

        String cuentaDestino = request.get("cuentaDestino") != null
                ? request.get("cuentaDestino").toString()
                : null;

        double monto;

        try {
            monto = Double.parseDouble(request.get("monto").toString());
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(new TransferenciaResponse(false, "El monto no es válido", 0, 0));
        }

        // Verificar el PIN antes de transferir
        if (!authService.validarPin(cuenta, pin)) {
            return ResponseEntity.badRequest()
                    .body(new TransferenciaResponse(false, "PIN incorrecto", 0, 0));
        }

        TransferenciaResponse respuesta = cuentaService.transferir(
                new TransferenciaRequest(cuenta, cuentaDestino, monto));

        return respuesta.exitoso()
                ? ResponseEntity.ok(respuesta)
                : ResponseEntity.badRequest().body(respuesta);
    }


    @GetMapping("/movimientos")
    public ResponseEntity<Object> movimientos(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @RequestParam(defaultValue = "20") int limite) {
        String cuenta = authService.celularDeEncabezado(auth);
        if (cuenta == null) {
            return ResponseEntity.status(401).body(Map.of("mensaje", SIN_SESION));
        }
        int limiteSeguro = Math.max(1, Math.min(limite, 100));
        return ResponseEntity.ok(cuentaService.historial(cuenta, limiteSeguro));
    }
}