ALTER TABLE cutting_plan
    ADD COLUMN total_stock_used_m DECIMAL(10, 2) NOT NULL DEFAULT 0 AFTER total_waste_m;
