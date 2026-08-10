package org.gridsuite.study.server.controller.dynamicsimulation;

import org.gridsuite.study.server.StudyApi;
import org.gridsuite.study.server.service.dynamicsimulation.DynamicSimulationService;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping(value = "/" + StudyApi.API_VERSION + "/dynamic-simulation")
public class DynamicSimulationParametersController {
    private final DynamicSimulationService dynamicSimulationService;

    public DynamicSimulationParametersController(DynamicSimulationService dynamicSimulationService) {
        this.dynamicSimulationService = dynamicSimulationService;
    }

    @GetMapping(value = "/providers")
    public ResponseEntity<String> getProviders() {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(dynamicSimulationService.getProviders());
    }

    @GetMapping(value = "/parameters/{parameterUuid}")
    public ResponseEntity<String> getParameters(@PathVariable UUID parameterUuid) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(dynamicSimulationService.getParameters(parameterUuid));
    }

    @PutMapping(value = "/parameters/{parameterUuid}")
    public ResponseEntity<Void> updateParameters(@PathVariable UUID parameterUuid, @RequestBody String parameters) {
        dynamicSimulationService.updateParameters(parameterUuid, parameters);
        return ResponseEntity.ok().build();
    }

    @GetMapping(value = "/results/{resultUuid}/download-debug-file")
    public ResponseEntity<Resource> downloadDebugFile(@PathVariable UUID resultUuid) {
        return dynamicSimulationService.downloadDebugFile(resultUuid);
    }
}
