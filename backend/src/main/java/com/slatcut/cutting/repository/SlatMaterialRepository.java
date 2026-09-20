package com.slatcut.cutting.repository;

import com.slatcut.cutting.domain.SlatMaterial;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface SlatMaterialRepository
        extends JpaRepository<SlatMaterial, Long>, JpaSpecificationExecutor<SlatMaterial> {

    Optional<SlatMaterial> findBySlatMaterial(Long slatMaterial);

    /**
     * Dấu vân trạng thái phía danh mục thanh nan. Nhóm vật tư ({@code slat_group}) quyết định công
     * thức cắt áp cho từng dòng định mức, nên một lần phân loại lại vật tư làm đổi kết quả thuật
     * toán y như sửa định mức — hiếm hơn nhiều, nhưng vẫn là đầu vào, và một dấu vân phủ ba trên
     * bốn đầu vào thì khó giải thích hơn hẳn một dấu vân phủ hết.
     */
    @Query("SELECT COUNT(sm) AS rowCount, MAX(sm.updatedAt) AS lastUpdatedAt FROM SlatMaterial sm")
    TableState readState();
}
