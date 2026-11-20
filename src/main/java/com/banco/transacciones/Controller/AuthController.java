package com.banco.transacciones.Controller;

import java.util.Map;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.dao.DuplicateKeyException;
import com.banco.transacciones.Record.LoginRequest;
import com.banco.transacciones.Record.LoginResponse;
import com.banco.transacciones.Record.RegistroRequest;
import com.banco.transacciones.Record.RegistroResponse;
import com.banco.transacciones.Service.AuthService;
import org.springframework.web.bind.annotation.PutMapping;
import com.banco.transacciones.Record.PerfilResponse;
import org.springframework.web.bind.annotation.GetMapping;

@RestController
@RequestMapping("/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/registro")
    public ResponseEntity<RegistroResponse> registro(@RequestBody RegistroRequest request) {
        try {
            RegistroResponse respuesta = authService.registrar(request);
            return respuesta.exitoso()
                    ? ResponseEntity.ok(respuesta)
                    : ResponseEntity.badRequest().body(respuesta);
        } catch (DuplicateKeyException e) {
            return ResponseEntity.badRequest()
                    .body(new RegistroResponse(false, "Ese celular ya está registrado"));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
        LoginResponse respuesta = authService.login(request);
        return respuesta.exitoso()
                ? ResponseEntity.ok(respuesta)
                : ResponseEntity.status(401).body(respuesta);
    }
    
    // Consultar perfil del usuario
    @GetMapping("/perfil")
    public ResponseEntity<?> consultarPerfil(
            @RequestHeader(value = "Authorization", required = false) String auth) {

        String celular = authService.celularDeEncabezado(auth);

        if (celular == null) {
            return ResponseEntity.status(401)
                    .body(new RegistroResponse(false, "Sesión no válida. Inicia sesión de nuevo"));
        }

        PerfilResponse perfil = authService.consultarPerfil(celular);

        if (perfil == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(perfil);
    }

    // Actualizar nombre y apellido
    @PutMapping("/perfil")
    public ResponseEntity<RegistroResponse> actualizarPerfil(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @RequestBody Map<String, String> request) {

        String celular = authService.celularDeEncabezado(auth);

        if (celular == null) {
            return ResponseEntity.status(401)
                    .body(new RegistroResponse(false, "Sesión no válida. Inicia sesión de nuevo"));
        }

        RegistroResponse respuesta = authService.actualizarPerfil(
                celular,
                request.get("nombre"),
                request.get("apellido")
        );

        return respuesta.exitoso()
                ? ResponseEntity.ok(respuesta)
                : ResponseEntity.badRequest().body(respuesta);
    }

    @PutMapping("/cambiar-contrasena")
    public ResponseEntity<RegistroResponse> cambiarContrasena(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @RequestBody Map<String, String> request) {

        String celular = authService.celularDeEncabezado(auth);

        if (celular == null) {
            return ResponseEntity.status(401)
                    .body(new RegistroResponse(false, "Sesión no válida. Inicia sesión de nuevo"));
        }

        RegistroResponse respuesta = authService.cambiarContrasena(
                celular,
                request.get("passwordActual"),
                request.get("passwordNueva")
        );

        return respuesta.exitoso()
                ? ResponseEntity.ok(respuesta)
                : ResponseEntity.badRequest().body(respuesta);
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(
            @RequestHeader(value = "Authorization", required = false) String auth) {
        authService.cerrarSesionConEncabezado(auth);
        return ResponseEntity.ok(Map.of("mensaje", "Sesión cerrada"));
    }
}