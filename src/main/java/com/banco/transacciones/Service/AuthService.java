package com.banco.transacciones.Service;

import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.banco.transacciones.Record.LoginRequest;
import com.banco.transacciones.Record.LoginResponse;
import com.banco.transacciones.Record.RegistroRequest;
import com.banco.transacciones.Record.RegistroResponse;

@Service
public class AuthService {

    private static final int EDAD_MINIMA = 18;

    private static class Sesion {
        final String celular;
        volatile long ultimaActividad;

        Sesion(String celular) {
            this.celular = celular;
            this.ultimaActividad = System.currentTimeMillis();
        }
    }

    private final JdbcTemplate jdbc;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private final long inactividadMs;

    // token -> sesión (vive en memoria)
    private final Map<String, Sesion> sesiones = new ConcurrentHashMap<>();

    public AuthService(JdbcTemplate jdbc,
            @Value("${banco.sesion.inactividad-segundos:300}") long inactividadSegundos) {
        this.jdbc = jdbc;
        this.inactividadMs = inactividadSegundos * 1000;
    }

    @Transactional
    public RegistroResponse registrar(RegistroRequest r) {
        if (r.nombre() == null || r.nombre().isBlank()) {
            return new RegistroResponse(false, "El nombre es obligatorio");
        }
        if (r.apellido() == null || r.apellido().isBlank()) {
            return new RegistroResponse(false, "El apellido es obligatorio");
        }
        if (r.documento() == null || r.documento().isBlank()) {
            return new RegistroResponse(false, "El documento es obligatorio");
        }

        LocalDate nacimiento;
        try {
            nacimiento = LocalDate.parse(r.fechaNacimiento());
        } catch (DateTimeParseException | NullPointerException e) {
            return new RegistroResponse(false, "La fecha de nacimiento debe tener el formato AAAA-MM-DD");
        }
        int edad = Period.between(nacimiento, LocalDate.now()).getYears();
        if (nacimiento.isAfter(LocalDate.now()) || edad > 120) {
            return new RegistroResponse(false, "La fecha de nacimiento no es válida");
        }
        if (edad < EDAD_MINIMA) {
            return new RegistroResponse(false, "Debes ser mayor de edad (18 años) para crear una cuenta");
        }

        if (r.celular() == null || !r.celular().matches("\\d{10}")) {
            return new RegistroResponse(false, "El celular debe tener 10 dígitos");
        }
        if (r.password() == null || r.password().length() < 4) {
            return new RegistroResponse(false, "La contraseña debe tener mínimo 4 caracteres");
        }

        Integer existe = jdbc.queryForObject(
                "SELECT COUNT(*) FROM usuarios WHERE celular = ?", Integer.class, r.celular());
        if (existe != null && existe > 0) {
            return new RegistroResponse(false, "Ese celular ya está registrado");
        }
        Integer cuentaExiste = jdbc.queryForObject(
                "SELECT COUNT(*) FROM cuentas WHERE numero = ?", Integer.class, r.celular());
        if (cuentaExiste != null && cuentaExiste > 0) {
            return new RegistroResponse(false, "Ese celular ya tiene una cuenta asociada");
        }

        jdbc.update("INSERT INTO usuarios (celular, nombre, apellido, documento, fecha_nacimiento, password_hash) "
                + "VALUES (?, ?, ?, ?, ?, ?)",
                r.celular(), r.nombre().trim(), r.apellido().trim(), r.documento().trim(),
                java.sql.Date.valueOf(nacimiento), encoder.encode(r.password()));
        jdbc.update("INSERT INTO cuentas (numero, saldo) VALUES (?, 0)", r.celular());

        return new RegistroResponse(true, "Cuenta creada. Tu número de cuenta es " + r.celular());
    }

    public LoginResponse login(LoginRequest r) {
        if (r.celular() == null || r.password() == null) {
            return new LoginResponse(false, "Celular o contraseña incorrectos", null, null);
        }

        List<Map<String, Object>> filas = jdbc.queryForList(
                "SELECT nombre, apellido, password_hash FROM usuarios WHERE celular = ?", r.celular());

        if (filas.isEmpty()
                || !encoder.matches(r.password(), (String) filas.get(0).get("PASSWORD_HASH"))) {
            return new LoginResponse(false, "Celular o contraseña incorrectos", null, null);
        }

        String token = UUID.randomUUID().toString();
        sesiones.put(token, new Sesion(r.celular()));
        String nombreCompleto = filas.get(0).get("NOMBRE") + " " + filas.get(0).get("APELLIDO");
        return new LoginResponse(true, "Bienvenido", token, nombreCompleto);
    }

    public String celularDeToken(String token) {
        if (token == null) {
            return null;
        }
        Sesion sesion = sesiones.get(token);
        if (sesion == null) {
            return null;
        }
        long ahora = System.currentTimeMillis();
        if (ahora - sesion.ultimaActividad > inactividadMs) {
            sesiones.remove(token);
            return null;
        }
        sesion.ultimaActividad = ahora;
        return sesion.celular;
    }

    public void cerrarSesion(String token) {
        if (token != null) {
            sesiones.remove(token);
        }
    }

    private String extraerToken(String encabezado) {
        if (encabezado == null || !encabezado.startsWith("Bearer ")) {
            return null;
        }
        return encabezado.substring(7).trim();
    }

    public String celularDeEncabezado(String encabezado) {
        return celularDeToken(extraerToken(encabezado));
    }

    public void cerrarSesionConEncabezado(String encabezado) {
        cerrarSesion(extraerToken(encabezado));
    }
}