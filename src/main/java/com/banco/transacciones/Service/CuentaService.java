package com.banco.transacciones.Service;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.banco.transacciones.Record.ConsignacionRequest;
import com.banco.transacciones.Record.ConsignacionResponse;
import com.banco.transacciones.Record.MovimientoResponse;
import com.banco.transacciones.Record.RetiroRequest;
import com.banco.transacciones.Record.RetiroResponse;
import com.banco.transacciones.Record.TransferenciaRequest;
import com.banco.transacciones.Record.TransferenciaResponse;
import com.sun.jna.ptr.DoubleByReference;
import com.sun.jna.ptr.IntByReference;

@Service
public class CuentaService {

    private final JdbcTemplate jdbc;
    private final long timeoutCobolMs;
    private final long retardoPruebaMs;

    // Hilos daemon: si alguno queda atascado, no impide cerrar la app
    private final ExecutorService ejecutorCobol = Executors.newCachedThreadPool(tarea -> {
        Thread hilo = new Thread(tarea, "cobol-worker");
        hilo.setDaemon(true);
        return hilo;
    });

    public CuentaService(JdbcTemplate jdbc,
            @Value("${banco.cobol.timeout-ms:3000}") long timeoutCobolMs,
            @Value("${banco.cobol.retardo-prueba-ms:0}") long retardoPruebaMs) {
        this.jdbc = jdbc;
        this.timeoutCobolMs = timeoutCobolMs;
        this.retardoPruebaMs = retardoPruebaMs;
    }

    // Corre una llamada a COBOL con límite de tiempo
    private void ejecutarCobol(Runnable llamada) {
        Future<?> tarea = ejecutorCobol.submit(() -> {
            if (retardoPruebaMs > 0) {
                try {
                    Thread.sleep(retardoPruebaMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            llamada.run();
        });
        try {
            tarea.get(timeoutCobolMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            tarea.cancel(true);
            throw new CobolTimeoutException("La llamada a COBOL superó " + timeoutCobolMs + " ms");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            tarea.cancel(true);
            throw new CobolTimeoutException("La llamada a COBOL fue interrumpida");
        } catch (ExecutionException e) {
            throw new RuntimeException(e.getCause());
        }
    }

    public boolean existeCuenta(String cuenta) {
        Integer cantidad = jdbc.queryForObject("SELECT COUNT(*) FROM cuentas WHERE numero = ?", Integer.class, cuenta);
        return cantidad != null && cantidad > 0;
    }

    private double leerSaldo(String cuenta) {
        return jdbc.queryForObject("SELECT saldo FROM cuentas WHERE numero = ?", Double.class, cuenta);
    }

    private void guardarSaldo(String cuenta, double nuevoSaldo) {
        jdbc.update("UPDATE cuentas SET saldo = ? WHERE numero = ?", nuevoSaldo, cuenta);
    }

    private double redondear(double valor) {
        return Math.round(valor * 100.0) / 100.0;
    }

    private void registrarMovimiento(String cuenta, String tipo, double monto, double gmf,
            double saldoDespues, String contraparte) {
        jdbc.update("INSERT INTO movimientos (cuenta, tipo, monto, gmf, saldo_despues, contraparte) "
                + "VALUES (?, ?, ?, ?, ?, ?)",
                cuenta, tipo, monto, gmf, saldoDespues, contraparte);
    }

    public List<MovimientoResponse> historial(String cuenta, int limite) {
        return jdbc.query(
                "SELECT id, tipo, monto, gmf, saldo_despues, contraparte, fecha "
                        + "FROM movimientos WHERE cuenta = ? ORDER BY id DESC LIMIT ?",
                (rs, fila) -> new MovimientoResponse(
                        rs.getLong("id"),
                        rs.getString("tipo"),
                        rs.getDouble("monto"),
                        rs.getDouble("gmf"),
                        rs.getDouble("saldo_despues"),
                        rs.getString("contraparte"),
                        rs.getTimestamp("fecha").toLocalDateTime().toString()),
                cuenta, limite);
    }

    // Operación COBOL: CALCULAR-GMF (4x1000)
    private double calcularGmf(double monto) {
        DoubleByReference refMonto = new DoubleByReference(monto);
        DoubleByReference refGmf = new DoubleByReference(0.0);
        IntByReference refEstado = new IntByReference(0);

        ejecutarCobol(() -> Transaccion.INSTANCE.CALCULAR__GMF(refMonto, refGmf, refEstado));

        return redondear(refGmf.getValue());
    }

    public synchronized double consultarSaldo(String cuenta) {
        DoubleByReference refSaldo = new DoubleByReference(leerSaldo(cuenta));
        DoubleByReference refResultado = new DoubleByReference(0.0);
        IntByReference refEstado = new IntByReference(0);

        ejecutarCobol(() -> Transaccion.INSTANCE.CONSULTAR__SALDO(refSaldo, refResultado, refEstado));

        return refResultado.getValue();
    }

    public synchronized RetiroResponse retirar(RetiroRequest request) {
        String cuenta = request.cuenta();
        if (!existeCuenta(cuenta)) {
            return new RetiroResponse(false, "La cuenta no existe", 0);
        }

        double saldo = leerSaldo(cuenta);

        if (request.monto() <= 0) {
            return new RetiroResponse(false, "El monto debe ser mayor a 0", saldo);
        }

        double gmf = calcularGmf(request.monto());
        double total = redondear(request.monto() + gmf);

        DoubleByReference refBalance = new DoubleByReference(saldo);
        DoubleByReference refMonto = new DoubleByReference(total);
        DoubleByReference refNuevoBalance = new DoubleByReference(0.0);
        IntByReference refEstado = new IntByReference(0);

        ejecutarCobol(() -> Transaccion.INSTANCE.RETIRAR__DINERO(refBalance, refMonto, refNuevoBalance, refEstado));

        if (refEstado.getValue() == 0) {
            saldo = redondear(refNuevoBalance.getValue());
            guardarSaldo(cuenta, saldo);
            registrarMovimiento(cuenta, "RETIRO", request.monto(), gmf, saldo, null);
            return new RetiroResponse(true, "Retiro exitoso. 4x1000 cobrado: " + gmf, saldo);
        }
        return new RetiroResponse(false, "Fondos insuficientes. Necesitas " + total + " (retiro + 4x1000)", saldo);
    }

    public synchronized ConsignacionResponse consignar(ConsignacionRequest request) {
        String cuenta = request.cuenta();
        if (!existeCuenta(cuenta)) {
            return new ConsignacionResponse(false, "La cuenta no existe", 0);
        }

        double saldo = leerSaldo(cuenta);

        if (request.monto() <= 0) {
            return new ConsignacionResponse(false, "El monto debe ser mayor a 0", saldo);
        }

        DoubleByReference refBalance = new DoubleByReference(saldo);
        DoubleByReference refMonto = new DoubleByReference(request.monto());
        DoubleByReference refNuevoBalance = new DoubleByReference(0.0);
        IntByReference refEstado = new IntByReference(0);

        ejecutarCobol(() -> Transaccion.INSTANCE.CONSIGNAR__DINERO(refBalance, refMonto, refNuevoBalance, refEstado));

        saldo = refNuevoBalance.getValue();
        guardarSaldo(cuenta, saldo);
        registrarMovimiento(cuenta, "CONSIGNACION", request.monto(), 0, saldo, null);
        return new ConsignacionResponse(true, "Consignación exitosa", saldo);
    }

    public synchronized TransferenciaResponse transferir(TransferenciaRequest request) {
        String origen = request.cuentaOrigen();
        String destino = request.cuentaDestino();

        if (!existeCuenta(origen) || !existeCuenta(destino)) {
            return new TransferenciaResponse(false, "La cuenta origen o destino no existe", 0, 0);
        }
        if (origen.equals(destino)) {
            return new TransferenciaResponse(false, "No puedes transferir a la misma cuenta", leerSaldo(origen), leerSaldo(destino));
        }

        double saldoOrigen = leerSaldo(origen);
        double saldoDestino = leerSaldo(destino);

        if (request.monto() <= 0) {
            return new TransferenciaResponse(false, "El monto debe ser mayor a 0", saldoOrigen, saldoDestino);
        }

        double gmf = calcularGmf(request.monto());

        DoubleByReference refOrigen = new DoubleByReference(saldoOrigen);
        DoubleByReference refDestino = new DoubleByReference(saldoDestino);
        DoubleByReference refMonto = new DoubleByReference(request.monto());
        DoubleByReference refGmf = new DoubleByReference(gmf);
        DoubleByReference refNuevoOrigen = new DoubleByReference(0.0);
        DoubleByReference refNuevoDestino = new DoubleByReference(0.0);
        IntByReference refEstado = new IntByReference(0);

        ejecutarCobol(() -> Transaccion.INSTANCE.TRANSFERIR__DINERO(refOrigen, refDestino, refMonto, refGmf,
                refNuevoOrigen, refNuevoDestino, refEstado));

        if (refEstado.getValue() == 0) {
            double nuevoOrigen = redondear(refNuevoOrigen.getValue());
            double nuevoDestino = redondear(refNuevoDestino.getValue());
            guardarSaldo(origen, nuevoOrigen);
            guardarSaldo(destino, nuevoDestino);
            registrarMovimiento(origen, "TRANSFERENCIA_ENVIADA", request.monto(), gmf, nuevoOrigen, destino);
            registrarMovimiento(destino, "TRANSFERENCIA_RECIBIDA", request.monto(), 0, nuevoDestino, origen);
            return new TransferenciaResponse(true, "Transferencia exitosa. 4x1000 cobrado: " + gmf,
                    nuevoOrigen, nuevoDestino);
        }
        return new TransferenciaResponse(false,
                "Fondos insuficientes. Necesitas " + redondear(request.monto() + gmf) + " (envío + 4x1000)",
                saldoOrigen, saldoDestino);
    }
}