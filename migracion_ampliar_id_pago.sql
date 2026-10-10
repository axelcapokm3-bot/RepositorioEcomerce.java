-- Stripe Checkout Session IDs pueden superar los 64 caracteres.
-- La aplicación guarda en mp_payment_id el ID externo de la sesión para
-- poder encontrar el intento cuando llega el webhook de Stripe.
ALTER TABLE pagos
    MODIFY COLUMN mp_payment_id VARCHAR(255) NULL;

-- Las URLs de Stripe Checkout pueden superar los 255 caracteres.
ALTER TABLE pagos
    MODIFY COLUMN url_pago TEXT NULL;
