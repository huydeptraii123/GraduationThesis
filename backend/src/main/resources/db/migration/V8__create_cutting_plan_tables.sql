CREATE TABLE cutting_plan (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    run_at             DATETIME       NOT NULL,
    status             ENUM('COMPLETED', 'FAILED') NOT NULL DEFAULT 'COMPLETED',
    total_waste_m      DECIMAL(10, 2) NOT NULL,
    scope_cutoff_date  DATE           NOT NULL,
    scope_order_count  INT            NOT NULL
);

CREATE TABLE cutting_plan_detail (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    cutting_plan_id   BIGINT       NOT NULL,
    slat_material_id  BIGINT       NOT NULL,
    source_length_mm  INT          NOT NULL,
    pattern_code      VARCHAR(100) NOT NULL,
    remainder_mm      INT          NOT NULL DEFAULT 0,
    remainder_type    ENUM('DISCARDED', 'WASTE', 'RESTOCK') NOT NULL,
    stick_count       INT          NOT NULL DEFAULT 1,
    CONSTRAINT fk_cutting_plan_detail_cutting_plan FOREIGN KEY (cutting_plan_id) REFERENCES cutting_plan (id),
    CONSTRAINT fk_cutting_plan_detail_slat_material FOREIGN KEY (slat_material_id) REFERENCES slat_material (id),
    INDEX idx_cutting_plan_detail_cutting_plan_id (cutting_plan_id)
);

CREATE TABLE cutting_plan_detail_item (
    id                       BIGINT AUTO_INCREMENT PRIMARY KEY,
    cutting_plan_detail_id   BIGINT  NOT NULL,
    sales_order_id           BIGINT  NOT NULL,
    cut_length_mm            INT     NOT NULL,
    cut_quantity             INT     NOT NULL,
    is_original_order        BOOLEAN NOT NULL,
    CONSTRAINT fk_cutting_plan_detail_item_detail FOREIGN KEY (cutting_plan_detail_id) REFERENCES cutting_plan_detail (id),
    CONSTRAINT fk_cutting_plan_detail_item_sales_order FOREIGN KEY (sales_order_id) REFERENCES sales_order (id),
    CONSTRAINT uk_cutting_plan_detail_item_detail_order UNIQUE (cutting_plan_detail_id, sales_order_id),
    INDEX idx_cutting_plan_detail_item_sales_order_id (sales_order_id)
);

CREATE TABLE shortage_record (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    cutting_plan_id   BIGINT         NOT NULL,
    sales_order_id    BIGINT         NOT NULL,
    slat_material_id  BIGINT         NOT NULL,
    missing_quantity  INT            NOT NULL,
    missing_length_m  DECIMAL(10, 2) NOT NULL,
    CONSTRAINT fk_shortage_record_cutting_plan FOREIGN KEY (cutting_plan_id) REFERENCES cutting_plan (id),
    CONSTRAINT fk_shortage_record_sales_order FOREIGN KEY (sales_order_id) REFERENCES sales_order (id),
    CONSTRAINT fk_shortage_record_slat_material FOREIGN KEY (slat_material_id) REFERENCES slat_material (id),
    CONSTRAINT uk_shortage_record_plan_order_material UNIQUE (cutting_plan_id, sales_order_id, slat_material_id),
    INDEX idx_shortage_record_sales_order_id (sales_order_id)
);
