CREATE TABLE customer (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer      BIGINT       NOT NULL,
    customer_name VARCHAR(255) NOT NULL,
    created_at    DATETIME     NOT NULL,
    updated_at    DATETIME     NOT NULL,
    CONSTRAINT uk_customer_code UNIQUE (customer)
);

CREATE TABLE sales_order (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    ycsx               VARCHAR(20)  NOT NULL,
    z_item             INT          NOT NULL,
    sales_document     BIGINT       NOT NULL,
    sales_order_item   INT          NOT NULL,
    customer_id        BIGINT       NOT NULL,
    door_product_id    BIGINT       NOT NULL,
    z_chieu_cao_dh     DECIMAL(6,3) NOT NULL,
    z_chieu_rong_dh    DECIMAL(6,3) NOT NULL,
    reqd_delivery_date DATE         NOT NULL,
    created_at         DATETIME     NOT NULL,
    updated_at         DATETIME     NOT NULL,
    CONSTRAINT uk_sales_order_ycsx_item UNIQUE (ycsx, z_item),
    CONSTRAINT uk_sales_order_sales_document_item UNIQUE (sales_document, sales_order_item),
    CONSTRAINT fk_sales_order_customer FOREIGN KEY (customer_id) REFERENCES customer (id),
    CONSTRAINT fk_sales_order_door_product FOREIGN KEY (door_product_id) REFERENCES door_product (id),
    INDEX idx_sales_order_reqd_delivery_date (reqd_delivery_date)
);
