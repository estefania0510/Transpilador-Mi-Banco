## Pruebas de la API con Postman

La aplicación corre en `http://localhost:8080`. En todas las peticiones con body, usa **Body → raw → JSON**.

Las operaciones de cuenta necesitan sesión. Después del login, copia el `token` de la respuesta (sin comillas) y pégalo en la pestaña **Authorization**, tipo **Bearer Token**. Las sesiones viven en memoria: al reiniciar la app hay que iniciar sesión otra vez.

### Resumen de endpoints

| Acción | Método | URL | Sesión |
|---|---|---|---|
| Registro | POST | `/auth/registro` | No |
| Login | POST | `/auth/login` | No |
| Cerrar sesión | POST | `/auth/logout` | Sí |
| Consultar saldo | GET | `/transacciones/saldo` | Sí |
| Consignar | PUT | `/transacciones/consignar` | Sí |
| Retirar | PUT | `/transacciones/retirar` | Sí |
| Transferir | POST | `/transacciones/transferir` | Sí |
| Movimientos | GET | `/transacciones/movimientos` | Sí |

### Registro

`POST /auth/registro`

```json
{
    "nombre": "Camila",
    "apellido": "Rojas",
    "documento": "1020304050",
    "fechaNacimiento": "1999-08-15",
    "celular": "3001234567",
    "password": "2424"
}
```

### Login

`POST /auth/login`

```json
{
    "celular": "3001234567",
    "password": "2424"
}
```

### Consignar

`PUT /transacciones/consignar`

```json
{
    "monto": 100
}
```

### Retirar

`PUT /transacciones/retirar`

```json
{
    "monto": 50
}
```

Se cobra el 4x1000 sobre el monto retirado.

### Transferir

`POST /transacciones/transferir`

```json
{
    "cuentaDestino": "3115052889",
    "monto": 50
}
```

La cuenta de origen sale de la sesión. Se cobra el 4x1000 al origen.

### Consultar saldo, movimientos y cerrar sesión

No llevan body, solo el token.

- `GET /transacciones/saldo`
- `GET /transacciones/movimientos` (por defecto los últimos 20; opcional `?limite=50`, máximo 100)
- `POST /auth/logout`

### Casos de error para probar

| Caso | Body | Resultado esperado |
|---|---|---|
| Menor de edad | Registro con `"fechaNacimiento": "2015-03-10"` | 400, rechazado |
| Celular repetido | Registro con un celular ya existente | 400, "Ese celular ya está registrado" |
| Contraseña incorrecta | Login con `"password": "0000"` | 401 |
| Fondos insuficientes | Retiro con `{ "monto": 999999 }` | 400, mensaje de fondos insuficientes |
| Monto inválido | Cualquier operación con `{ "monto": 0 }` | 400, "El monto debe ser mayor a 0" |
| Sin token o token falso | Cualquier petición con sesión | 401, "Sesión no válida" |

### Tiempos límite

- **Inactividad:** la sesión se cierra tras 300 segundos sin peticiones (`banco.sesion.inactividad-segundos`).
- **COBOL:** cada llamada tiene un límite de 3000 ms (`banco.cobol.timeout-ms`). Si se pasa, responde 504 y no se hace ningún cambio.
