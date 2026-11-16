package com.banco.transacciones.Service;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.ptr.DoubleByReference;
import com.sun.jna.ptr.IntByReference;

public interface Transaccion extends Library {
    Transaccion INSTANCE = Native.load("transaccion", Transaccion.class);

    void RETIRAR__DINERO(DoubleByReference saldoInicial,
                        DoubleByReference montoRetirar,
                        DoubleByReference saldoActual,
                        IntByReference estado);

    void CONSIGNAR__DINERO(DoubleByReference saldoInicial,
                           DoubleByReference montoConsignar,
                           DoubleByReference saldoActual,
                           IntByReference estado);

    void CONSULTAR__SALDO(DoubleByReference saldoCuenta,
                        DoubleByReference saldoActual,
                        IntByReference estado);

    void TRANSFERIR__DINERO(DoubleByReference saldoOrigen,
                            DoubleByReference saldoDestino,
                            DoubleByReference montoTransferir,
                            DoubleByReference comision,
                            DoubleByReference nuevoOrigen,
                            DoubleByReference nuevoDestino,
                            IntByReference estado);

    void CALCULAR__GMF(DoubleByReference montoBase,
                        DoubleByReference valorGmf,
                        IntByReference estado);

}