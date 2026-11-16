       IDENTIFICATION DIVISION.
       PROGRAM-ID. CALCULAR-GMF.
       DATA DIVISION.
       LINKAGE SECTION.
       01 MONTO-BASE        USAGE COMP-2.
       01 VALOR-GMF         USAGE COMP-2.
       01 ESTADO            PIC S9(9) COMP-5.
       PROCEDURE DIVISION USING MONTO-BASE
                                VALOR-GMF
                                ESTADO.
       MAIN-PROCEDURE.
           COMPUTE VALOR-GMF = MONTO-BASE * 4 / 1000
           MOVE 0 TO ESTADO
           GOBACK.
       END PROGRAM CALCULAR-GMF.