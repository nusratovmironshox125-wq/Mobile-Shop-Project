-- Additive migration. Historical facts and mixed specifications are never rewritten.
CREATE TABLE lead_read (
    admin_id bigint NOT NULL REFERENCES admin_user,
    lead_id bigint NOT NULL REFERENCES lead,
    read_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (admin_id, lead_id)
);
ALTER TABLE product_variant ADD COLUMN storage_type varchar(30);
ALTER TABLE product_variant ADD COLUMN specs_review_required boolean NOT NULL DEFAULT false;
UPDATE product_variant v SET specs_review_required=true
FROM product p JOIN category c ON c.id=p.category_id
WHERE v.product_id=p.id AND (
 (c.kind='PHONE' AND (nullif(btrim(v.gpu),'') IS NOT NULL OR nullif(btrim(v.weight),'') IS NOT NULL))
 OR (c.kind='LAPTOP' AND (nullif(btrim(v.cameras),'') IS NOT NULL OR nullif(btrim(v.battery),'') IS NOT NULL OR nullif(btrim(v.sim),'') IS NOT NULL))
 OR c.kind='GENERAL'
);
-- Fails atomically on ambiguous duplicates. Preflight must resolve them explicitly.
CREATE UNIQUE INDEX variant_sku_normalized_uq ON product_variant (upper(btrim(sku)));
ALTER TABLE audit_event ADD COLUMN actor_username varchar(60);
ALTER TABLE audit_event ADD COLUMN actor_role varchar(8);
ALTER TABLE audit_event ADD COLUMN object_label varchar(255);
ALTER TABLE audit_event ADD COLUMN changes jsonb NOT NULL DEFAULT '{}'::jsonb;
ALTER TABLE audit_event ADD COLUMN outcome varchar(16);
CREATE INDEX audit_actor_idx ON audit_event (actor_id,created_at DESC,id DESC);
CREATE INDEX audit_action_idx ON audit_event (action,created_at DESC,id DESC);
ALTER TABLE lead ALTER COLUMN variant_label TYPE varchar(2000);
