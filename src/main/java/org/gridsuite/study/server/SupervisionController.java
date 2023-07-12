package org.gridsuite.study.server;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.gridsuite.study.server.dto.CreatedStudyBasicInfos;
import org.gridsuite.study.server.dto.supervision.SupervisionStudyInfos;
import org.gridsuite.study.server.service.StudyService;
import org.gridsuite.study.server.service.SupervisionService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping(value = "/" + StudyApi.API_VERSION + "/supervision")
@Tag(name = "Study server - Supervision")
public class SupervisionController {
    private final SupervisionService supervisionService;

    public SupervisionController(SupervisionService supervisionService) {
        this.supervisionService = supervisionService;
    }

    @GetMapping(value = "/studies")
    @Operation(summary = "Get all studies")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The list of studies")})
    public ResponseEntity<List<SupervisionStudyInfos>> getStudyList() {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(supervisionService.getStudies());
    }
}
