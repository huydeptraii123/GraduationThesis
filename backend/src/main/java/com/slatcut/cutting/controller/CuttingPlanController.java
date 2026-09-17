package com.slatcut.cutting.controller;

import com.slatcut.cutting.domain.CuttingPlan;
import com.slatcut.cutting.dto.CuttingPlanResponse;
import com.slatcut.cutting.dto.CuttingPlanScopePreviewResponse;
import com.slatcut.cutting.dto.CuttingPlanSummaryResponse;
import com.slatcut.cutting.service.CuttingPlanService;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/cutting-plans")
public class CuttingPlanController {

    private final CuttingPlanService service;

    public CuttingPlanController(CuttingPlanService service) {
        this.service = service;
    }

    @PostMapping("/generate")
    @PreAuthorize("hasRole('PLANNER')")
    public CuttingPlanResponse generate() {
        CuttingPlan plan = service.generate();
        return service.getById(plan.getId());
    }

    @GetMapping("/scope-preview")
    @PreAuthorize("hasRole('PLANNER')")
    public CuttingPlanScopePreviewResponse getScopePreview() {
        return service.getScopePreview();
    }

    @GetMapping
    public List<CuttingPlanSummaryResponse> getAll() {
        return service.getSummaries();
    }

    @GetMapping("/{id}")
    public CuttingPlanResponse getById(@PathVariable Long id) {
        return service.getById(id);
    }
}
