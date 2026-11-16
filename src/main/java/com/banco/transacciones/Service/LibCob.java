package com.banco.transacciones.Service;

import com.sun.jna.Library;
import com.sun.jna.Native;

public interface LibCob extends Library {
    LibCob INSTANCE = Native.load("libcob-4", LibCob.class);
    int cob_init(int argc, String[] argv);
}