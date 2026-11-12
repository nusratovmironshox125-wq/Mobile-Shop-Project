\set ON_ERROR_STOP on
BEGIN;
DO $$ BEGIN
 IF current_database() <> 'rms_load' THEN RAISE EXCEPTION 'Dedicated rms_load database required'; END IF;
 IF EXISTS(SELECT 1 FROM product) OR EXISTS(SELECT 1 FROM lead) THEN RAISE EXCEPTION 'Empty load fixture required'; END IF;
END $$;
UPDATE shop_settings SET name='RMS LOAD ONLY';
INSERT INTO product(category_id,brand,name,slug,status)
SELECT (SELECT id FROM category WHERE kind=CASE WHEN n%2=0 THEN 'PHONE' ELSE 'LAPTOP' END LIMIT 1),
 'SYNTHETIC','Load model '||n,'load-model-'||n,'PUBLISHED' FROM generate_series(1,10000) n;
INSERT INTO product_variant(product_id,sku,stock,price,color,ram_gb,storage_gb,sale_price)
SELECT p.id,'LOAD-'||p.id||'-'||v,'IN_STOCK',2000000+p.id*10+v*100000,
 CASE WHEN v=1 THEN 'Black' ELSE 'White' END,8*v,128*v,
 CASE WHEN p.id%3=0 THEN 1900000+p.id*10+v*100000 END
FROM product p CROSS JOIN generate_series(1,2) v;
INSERT INTO product_image(product_id,storage_key,width,height,alt)
SELECT p.id,gen_random_uuid(),600,430,'Synthetic metadata only' FROM product p CROSS JOIN generate_series(1,4);
INSERT INTO lead(public_number,idempotency_key,request_hash,product_id,variant_id,product_name,sku,variant_label,
 price,installment,status,privacy_version)
SELECT gen_random_uuid(),gen_random_uuid(),md5(n::text),p.id,v.id,p.name,v.sku,'Color: Black',v.price,false,'NEW','load'
FROM generate_series(1,100000) n JOIN product p ON p.id=(SELECT min(id) FROM product)+(n-1)%10000
JOIN LATERAL(SELECT * FROM product_variant WHERE product_id=p.id ORDER BY id LIMIT 1) v ON true;
ANALYZE;
COMMIT;
