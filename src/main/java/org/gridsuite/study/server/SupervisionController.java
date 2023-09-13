package org.gridsuite.study.server;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.gridsuite.study.server.service.SupervisionService;
import org.gridsuite.study.server.utils.ComputationType;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(value = "/" + StudyApi.API_VERSION + "/supervision")
@Tag(name = "Study server - Supervision")
public class SupervisionController {
    private final SupervisionService supervisionService;

    public SupervisionController(SupervisionService supervisionService) {
        this.supervisionService = supervisionService;
    }

    @DeleteMapping(value = "/computation/results")
    @Operation(summary = "delete all results of a given computation")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "all loadflow results have been deleted")})
    public ResponseEntity<Integer> deleteSimulationResults(@Parameter(description = "Computation type") @RequestParam("type") ComputationType computationType,
                                                           @Parameter(description = "Dry run") @RequestParam("dryRun") boolean dryRun) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(supervisionService.deleteComputationResults(computationType, dryRun));
    }
}
