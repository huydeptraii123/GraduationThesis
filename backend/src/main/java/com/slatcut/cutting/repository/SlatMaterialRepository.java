package com.slatcut.cutting.repository;

import com.slatcut.cutting.domain.SlatMaterial;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SlatMaterialRepository extends JpaRepository<SlatMaterial, Long> {

    Optional<SlatMaterial> findBySlatMaterial(Long slatMaterial);
}
