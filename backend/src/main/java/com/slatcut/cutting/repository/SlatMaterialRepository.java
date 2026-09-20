package com.slatcut.cutting.repository;

import com.slatcut.cutting.domain.SlatMaterial;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface SlatMaterialRepository
        extends JpaRepository<SlatMaterial, Long>, JpaSpecificationExecutor<SlatMaterial> {

    Optional<SlatMaterial> findBySlatMaterial(Long slatMaterial);
}
