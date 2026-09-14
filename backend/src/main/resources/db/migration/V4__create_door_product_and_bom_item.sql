CREATE TABLE door_product (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    material           BIGINT       NOT NULL,
    door_material_name VARCHAR(255) NOT NULL,
    z_mau_sac          VARCHAR(20)  NOT NULL,
    created_at         DATETIME     NOT NULL,
    updated_at         DATETIME     NOT NULL,
    CONSTRAINT uk_door_product_material_color UNIQUE (material, z_mau_sac)
);

CREATE TABLE bom_item (
    id                       BIGINT AUTO_INCREMENT PRIMARY KEY,
    door_product_id          BIGINT        NOT NULL,
    slat_material_id         BIGINT        NOT NULL,
    width_offset_m           DECIMAL(6,3)  NULL,
    height_offset_m          DECIMAL(6,3)  NULL,
    slat_count_slope         DECIMAL(10,6) NULL,
    slat_count_intercept     DECIMAL(10,4) NULL,
    dinh_muc_tb_m_per_bo_cua DECIMAL(10,4) NULL,
    created_at               DATETIME      NOT NULL,
    updated_at               DATETIME      NOT NULL,
    CONSTRAINT uk_bom_item_door_product_slat_material UNIQUE (door_product_id, slat_material_id),
    CONSTRAINT fk_bom_item_door_product FOREIGN KEY (door_product_id) REFERENCES door_product (id),
    CONSTRAINT fk_bom_item_slat_material FOREIGN KEY (slat_material_id) REFERENCES slat_material (id)
);
