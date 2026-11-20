-- Business invariants live in the schema, so they hold for every instance and every code path.

CREATE TABLE coupon (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code         VARCHAR(32) NOT NULL,
    max_uses     INTEGER     NOT NULL,
    current_uses INTEGER     NOT NULL DEFAULT 0,
    country_code VARCHAR(2)  NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL,
    -- BR-1: one canonical (upper-case) form, so a plain UNIQUE is case-insensitive.
    CONSTRAINT uq_coupon_code UNIQUE (code),
    CONSTRAINT ck_coupon_code_normalized CHECK (code = UPPER(code)),
    CONSTRAINT ck_coupon_country_code_format CHECK (country_code ~ '^[A-Z]{2}$'),
    CONSTRAINT ck_coupon_max_uses_positive CHECK (max_uses > 0),
    -- BR-2: last line of defence – the counter can never exceed the limit.
    CONSTRAINT ck_coupon_uses_within_limit CHECK (current_uses BETWEEN 0 AND max_uses)
);

CREATE TABLE coupon_redemption (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    coupon_id   BIGINT      NOT NULL REFERENCES coupon (id),
    user_id     VARCHAR(64) NOT NULL,
    redeemed_at TIMESTAMPTZ NOT NULL,
    -- BR-4: one redemption per user per coupon.
    CONSTRAINT uq_redemption_coupon_user UNIQUE (coupon_id, user_id)
);
