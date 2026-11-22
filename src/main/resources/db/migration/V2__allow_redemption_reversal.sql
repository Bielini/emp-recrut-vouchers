-- BR-6: a redemption can be reversed (cancelled or returned order). The row is kept for audit
-- and marked as reversed; only active (non-reversed) redemptions count towards BR-4 and BR-2.

ALTER TABLE coupon_redemption
    ADD COLUMN reversed_at TIMESTAMPTZ,
    ADD CONSTRAINT ck_redemption_reversed_after_redeemed CHECK (reversed_at >= redeemed_at);

-- BR-4 now means: at most one ACTIVE redemption per user and coupon. A partial unique index
-- lets the same user redeem again after a reversal while history rows accumulate.
ALTER TABLE coupon_redemption DROP CONSTRAINT uq_redemption_coupon_user;
CREATE UNIQUE INDEX uq_redemption_coupon_user ON coupon_redemption (coupon_id, user_id) WHERE reversed_at IS NULL;
