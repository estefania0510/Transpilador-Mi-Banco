package com.banco.transacciones;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import com.banco.transacciones.Service.LibCob;

@SpringBootApplication
public class TransaccionesApplication {

    public static void main(String[] args) {
        LibCob.INSTANCE.cob_init(0, null);
        SpringApplication.run(TransaccionesApplication.class, args);
    }
}