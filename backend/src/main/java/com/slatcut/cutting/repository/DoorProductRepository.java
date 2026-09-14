package com.slatcut.cutting.repository;

import com.slatcut.cutting.domain.DoorProduct;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DoorProductRepository extends JpaRepository<DoorProduct, Long> {

    Optional<DoorProduct> findByMaterialAndMauSac(Long material, String mauSac);
}
