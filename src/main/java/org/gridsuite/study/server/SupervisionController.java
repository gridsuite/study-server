package org.gridsuite.study.server;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.gridsuite.study.server.service.SupervisionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/" + StudyApi.API_VERSION + "/supervision")
@Tag(name = "Study server - Supervision")
public class SupervisionController {
    private final SupervisionService supervisionService;

    public SupervisionController(SupervisionService supervisionService) {
        this.supervisionService = supervisionService;
    }

    @DeleteMapping(value = "/loadflow/results")
    @Operation(summary = "delete all studies loadflow results")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "all loadflow results have been deleted")})
    public ResponseEntity<Void> deleteLoadflowResults() {
        supervisionService.deleteLoadflowResults();
        return ResponseEntity.ok().build();
    }

    @DeleteMapping(value = "/dynamic-simulation/results")
    @Operation(summary = "delete all studies dynamic simulation analysis results")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "all dynamic simulation results have been deleted")})
    public ResponseEntity<Void> deleteDynamicSimulationResults() {
        supervisionService.deleteDynamicSimulationResults();
        return ResponseEntity.ok().build();
    }

    @DeleteMapping(value = "/security-analysis/results")
    @Operation(summary = "delete all studies security analysis results")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "all security analysis results have been deleted")})
    public ResponseEntity<Void> deleteSecurityAnalysisResults() {
        supervisionService.deleteSecurityAnalysisResults();
        return ResponseEntity.ok().build();
    }

    @DeleteMapping(value = "/sensitivity-analysis/results")
    @Operation(summary = "delete all studies sensitivity analysis results")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "all sensitivity analysis results have been deleted")})
    public ResponseEntity<Void> deleteSensitivityAnalysisResults() {
        supervisionService.deleteSensitivityAnalysisResults();
        return ResponseEntity.ok().build();
    }

    @DeleteMapping(value = "/shortcircuit/results")
    @Operation(summary = "delete all studies shortcircuit results")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "all shortcircuit results have been deleted")})
    public ResponseEntity<Void> deleteShortcircuitResults() {
        supervisionService.deleteShortcircuitResults();
        return ResponseEntity.ok().build();
    }

    @DeleteMapping(value = "/voltage-init/results")
    @Operation(summary = "delete all studies voltage init results")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "all voltage init results have been deleted")})
    public ResponseEntity<Void> deleteVoltageInitResults() {
        supervisionService.deleteVoltageInitResults();
        return ResponseEntity.ok().build();
    }
}
