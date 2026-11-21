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
import com.banco.transacciones.Record.PerfilResponse;

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

        // Validar nombres y apellidos: solo letras, espacios, tildes y guiones
    private boolean nombreValido(String nombre) {
        return nombre != null
                && nombre.trim().matches("[\\p{L}\\p{M}]+(?:[ '\\-][\\p{L}\\p{M}]+)*");
    }

    // Validar que el PIN tenga cuatro dígitos y no sea fácil de adivinar
    private boolean pinValido(String pin) {
        if (pin == null || !pin.matches("\\d{4}")) {
            return false;
        }

        // Rechazar combinaciones repetidas: 0000, 1111, 2222...
        if (pin.matches("(\\d)\\1{3}")) {
            return false;
        }

        // Rechazar secuencias consecutivas: 1234, 2345, 4321, 9876...
        int diferencia = pin.charAt(1) - pin.charAt(0);

        if (Math.abs(diferencia) == 1
                && pin.charAt(2) - pin.charAt(1) == diferencia
                && pin.charAt(3) - pin.charAt(2) == diferencia) {
            return false;
        }
        return true;
    }

    @Transactional
    public RegistroResponse registrar(RegistroRequest r) {

        if (r.nombre() == null || r.nombre().isBlank()) {
            return new RegistroResponse(false, "El nombre es obligatorio");
        }

        if (r.apellido() == null || r.apellido().isBlank()) {
            return new RegistroResponse(false, "El apellido es obligatorio");
        }

        // Validar que el nombre y apellido solo contengan letras
        if (!r.nombre().trim().matches("[\\p{L}]+(?:[ '-][\\p{L}]+)*")) {
            return new RegistroResponse(false,
                    "El nombre solo puede contener letras");
        }

        if (!r.apellido().trim().matches("[\\p{L}]+(?:[ '-][\\p{L}]+)*")) {
            return new RegistroResponse(false,
                    "El apellido solo puede contener letras");
        }

        if (r.nombre().trim().length() > 60 || r.apellido().trim().length() > 60) {
            return new RegistroResponse(false,
                    "El nombre y apellido no pueden superar los 60 caracteres");
        }

        if (!nombreValido(r.nombre()) || !nombreValido(r.apellido())) {
            return new RegistroResponse(false,
                    "El nombre y apellido solo pueden contener letras");
        }

        if (r.nombre().trim().length() > 60 || r.apellido().trim().length() > 60) {
            return new RegistroResponse(false,
                    "El nombre y apellido no pueden superar los 60 caracteres");
        }

        if (r.documento() == null || r.documento().isBlank()) {
            return new RegistroResponse(false, "El documento es obligatorio");
        }

        String documento = r.documento().trim();

        Integer documentoExiste = jdbc.queryForObject(
                "SELECT COUNT(*) FROM usuarios WHERE documento = ?",
                Integer.class,
                documento
        );

        if (documentoExiste != null && documentoExiste > 0) {
            return new RegistroResponse(false,
                    "Este documento ya se encuentra registrado");
        }

        LocalDate nacimiento;

        try {
            nacimiento = LocalDate.parse(r.fechaNacimiento());
        } catch (DateTimeParseException | NullPointerException e) {
            return new RegistroResponse(false,
                    "La fecha de nacimiento debe tener el formato AAAA-MM-DD");
        }

        int edad = Period.between(nacimiento, LocalDate.now()).getYears();

        if (nacimiento.isAfter(LocalDate.now()) || edad > 120) {
            return new RegistroResponse(false, "La fecha de nacimiento no es válida");
        }

        if (edad < EDAD_MINIMA) {
            return new RegistroResponse(false,
                    "Debes ser mayor de edad (18 años) para crear una cuenta");
        }

        if (r.celular() == null || !r.celular().matches("\\d{10}")) {
            return new RegistroResponse(false, "El celular debe tener 10 dígitos");
        }

        if (!pinValido(r.password())) {
        return new RegistroResponse(false, "La contraseña debe tener cuatro dígitos y no puede ser una combinación sencilla");
        }

        Integer existe = jdbc.queryForObject(
                "SELECT COUNT(*) FROM usuarios WHERE celular = ?", Integer.class, r.celular());

        if (existe != null && existe > 0) {
            return new RegistroResponse(false, "Ese celular ya está registrado");
        }

        Integer cuentaExiste = jdbc.queryForObject(
                "SELECT COUNT(*) FROM cuentas WHERE numero = ?",
                Integer.class,
                r.celular()
        );

        if (cuentaExiste != null && cuentaExiste > 0) {
            return new RegistroResponse(false,
                    "Ese celular ya tiene una cuenta asociada");
        }

        jdbc.update(
                "INSERT INTO usuarios (celular, nombre, apellido, documento, fecha_nacimiento, password_hash) "
                + "VALUES (?, ?, ?, ?, ?, ?)",
                r.celular(), r.nombre().trim(), r.apellido().trim(), documento,
                java.sql.Date.valueOf(nacimiento),
                encoder.encode(r.password())
        );

        jdbc.update(
                "INSERT INTO cuentas (numero, saldo) VALUES (?, 0)",
                r.celular()
        );

        return new RegistroResponse(true,
                "Cuenta creada. Tu número de cuenta es " + r.celular());
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

    // Verificar el PIN de seguridad del usuario
    public boolean validarPin(String celular, String pin) {

        if (celular == null || pin == null || !pin.matches("\\d{4}")) {
            return false;
        }

        List<Map<String, Object>> filas = jdbc.queryForList(
                "SELECT password_hash FROM usuarios WHERE celular = ?",
                celular
        );

        if (filas.isEmpty()) {
            return false;
        }

        String passwordHash = (String) filas.get(0).get("PASSWORD_HASH");

        return encoder.matches(pin, passwordHash);
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

    @Transactional
    public RegistroResponse cambiarContrasena(
            String celular,
            String passwordActual,
            String passwordNueva) {

        if (passwordActual == null || passwordActual.isBlank()) {
            return new RegistroResponse(false,
                    "Debes ingresar tu contraseña actual");
        }

        if (!pinValido(passwordNueva)) {
        return new RegistroResponse(false, "La nueva contraseña debe tener cuatro dígitos y no puede ser una combinación sencilla");
        }

        String passwordHash = jdbc.queryForObject(
                "SELECT password_hash FROM usuarios WHERE celular = ?",
                String.class,
                celular
        );

        if (!encoder.matches(passwordActual, passwordHash)) {
            return new RegistroResponse(false,
                    "La contraseña actual es incorrecta");
        }

        if (encoder.matches(passwordNueva, passwordHash)) {
            return new RegistroResponse(false,
                    "La nueva contraseña debe ser diferente a la actual");
        }

        jdbc.update(
                "UPDATE usuarios SET password_hash = ? WHERE celular = ?",
                encoder.encode(passwordNueva),
                celular
        );

        return new RegistroResponse(true,
                "La contraseña se cambió correctamente");
    }

        // Consultar los datos del perfil
        public PerfilResponse consultarPerfil(String celular) {

            List<Map<String, Object>> filas = jdbc.queryForList(
                    "SELECT nombre, apellido, documento, celular, fecha_nacimiento "
                    + "FROM usuarios WHERE celular = ?",
                    celular
            );

            if (filas.isEmpty()) {
                return null;
            }

            Map<String, Object> usuario = filas.get(0);

            return new PerfilResponse(
                    (String) usuario.get("NOMBRE"),
                    (String) usuario.get("APELLIDO"),
                    (String) usuario.get("DOCUMENTO"),
                    (String) usuario.get("CELULAR"),
                    usuario.get("FECHA_NACIMIENTO").toString()
            );
        }

    // Actualizar nombre y apellido
    @Transactional
    public RegistroResponse actualizarPerfil(
            String celular,
            String nombre,
            String apellido) {

        if (nombre == null || nombre.isBlank()) {
            return new RegistroResponse(false, "El nombre es obligatorio");
        }

        if (apellido == null || apellido.isBlank()) {
            return new RegistroResponse(false, "El apellido es obligatorio");
        }

        // Validar que el nombre y apellido solo contengan letras
        if (!nombre.trim().matches("[\\p{L}]+(?:[ '-][\\p{L}]+)*")) {
            return new RegistroResponse(false,
                    "El nombre solo puede contener letras");
        }

        if (!apellido.trim().matches("[\\p{L}]+(?:[ '-][\\p{L}]+)*")) {
            return new RegistroResponse(false,
                    "El apellido solo puede contener letras");
        }

        if (!nombreValido(nombre) || !nombreValido(apellido)) {
        return new RegistroResponse(false, "El nombre y apellido solo pueden contener letras");
        }

        if (nombre.trim().length() > 60 || apellido.trim().length() > 60) {
            return new RegistroResponse(false, "El nombre y apellido no pueden superar los 60 caracteres");
        }

        jdbc.update(
                "UPDATE usuarios SET nombre = ?, apellido = ? WHERE celular = ?",
                nombre.trim(),
                apellido.trim(),
                celular
        );

        return new RegistroResponse(true, "Los datos del perfil se actualizaron correctamente");
    }
}