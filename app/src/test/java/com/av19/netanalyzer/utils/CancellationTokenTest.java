package com.av19.netanalyzer.utils;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CancellationTokenTest {

    @Test
    public void initialState_isNotCancelled() {
        CancellationToken token = new CancellationToken();
        assertFalse("El token no debería estar cancelado inicialmente", token.isCancelled());
    }

    @Test
    public void cancel_setsCancelledFlag() {
        CancellationToken token = new CancellationToken();
        token.cancel();
        assertTrue("El token debe estar cancelado después de llamar a cancel()", token.isCancelled());
    }

    @Test
    public void cancel_isIdempotent() {
        CancellationToken token = new CancellationToken();
        token.cancel();
        token.cancel(); // segunda llamada no debería cambiar nada
        assertTrue(token.isCancelled());
    }

    @Test
    public void multipleTokens_areIndependent() {
        CancellationToken token1 = new CancellationToken();
        CancellationToken token2 = new CancellationToken();

        token1.cancel();

        assertTrue(token1.isCancelled());
        assertFalse(token2.isCancelled());
    }

    @Test
    public void cancellation_isVolatile_shouldBeVisibleAcrossThreads() throws InterruptedException {
        final CancellationToken token = new CancellationToken();
        final boolean[] threadObserved = {false};

        Thread reader = new Thread(() -> {
            // Esperar activamente hasta que el token sea cancelado (simula uso real)
            while (!token.isCancelled()) {
                Thread.yield();
            }
            threadObserved[0] = true;
        });

        reader.start();
        // Pequeña pausa para asegurar que el hilo lector está en el bucle
        Thread.sleep(50);
        token.cancel();
        reader.join(1000); // esperar a que termine

        assertTrue(threadObserved[0]);
    }
}