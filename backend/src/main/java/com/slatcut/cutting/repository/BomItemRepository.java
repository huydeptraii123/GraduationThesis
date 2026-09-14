package com.slatcut.cutting.repository;

import com.slatcut.cutting.domain.BomItem;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BomItemRepository extends JpaRepository<BomItem, Long> {

    @Override
    @EntityGraph(attributePaths = {"doorProduct", "slatMaterial"})
    List<BomItem> findAll();

    Optional<BomItem> findByDoorProduct_IdAndSlatMaterial_Id(Long doorProductId, Long slatMaterialId);

    boolean existsByDoorProduct_Id(Long doorProductId);

    boolean existsBySlatMaterial_Id(Long slatMaterialId);
}
