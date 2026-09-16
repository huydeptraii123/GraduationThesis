package com.slatcut.cutting.repository;

import com.slatcut.cutting.domain.ShortageRecord;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShortageRecordRepository extends JpaRepository<ShortageRecord, Long> {

    boolean existsBySalesOrder_Id(Long salesOrderId);
}
