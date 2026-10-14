package com.shoplab.api.order;

/** Forma de pago elegida en la app. No hay pasarela real (§1). */
public enum PaymentMethod {
    CARD,
    CASH,
    TRANSFER
}
