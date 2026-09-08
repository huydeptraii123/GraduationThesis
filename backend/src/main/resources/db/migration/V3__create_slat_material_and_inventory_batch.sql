CREATE TABLE slat_material (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    slat_material      BIGINT       NOT NULL,
    slat_material_name VARCHAR(255) NOT NULL,
    slat_group         ENUM('MAIN_SLAT', 'SUB_SLAT', 'BOTTOM_BAR', 'RAIL', 'OTHER') NOT NULL,
    created_at         DATETIME     NOT NULL,
    updated_at         DATETIME     NOT NULL,
    CONSTRAINT uk_slat_material_code UNIQUE (slat_material)
);

CREATE TABLE inventory_batch (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    slat_material_id  BIGINT   NOT NULL,
    do_dai_thanh_mm   INT      NOT NULL,
    so_thanh          INT      NOT NULL DEFAULT 0,
    stock_status      ENUM('OVER_6_MONTHS', 'BETWEEN_3_AND_6_MONTHS', 'UNDER_3_MONTHS') NOT NULL,
    created_at        DATETIME NOT NULL,
    updated_at        DATETIME NOT NULL,
    CONSTRAINT uk_inventory_batch_material_length UNIQUE (slat_material_id, do_dai_thanh_mm),
    CONSTRAINT fk_inventory_batch_slat_material FOREIGN KEY (slat_material_id) REFERENCES slat_material (id)
);
