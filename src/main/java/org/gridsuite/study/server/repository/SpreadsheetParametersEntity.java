package org.gridsuite.study.server.repository;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;
import lombok.Builder.Default;
import org.gridsuite.study.server.dto.SpreadsheetParameters;
import org.gridsuite.study.server.dto.SpreadsheetParameters.BranchSpreadsheetParameters;
import org.gridsuite.study.server.dto.SpreadsheetParameters.GeneratorSpreadsheetParameters;

@Embeddable
@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
public class SpreadsheetParametersEntity {
    @Column(name = "sp_load_branch_olg", nullable = false, columnDefinition = "boolean default false")
    @Default
    private boolean spreadsheetLoadBranchOperationalLimitGroup = false;

    @Column(name = "sp_load_line_olg", nullable = false, columnDefinition = "boolean default false")
    @Default
    private boolean spreadsheetLoadLineOperationalLimitGroup = false;

    @Column(name = "sp_load_2wt_olg", nullable = false, columnDefinition = "boolean default false")
    @Default
    private boolean spreadsheetLoad2wtOperationalLimitGroup = false;

    @Column(name = "sp_load_generator_rt", nullable = false, columnDefinition = "boolean default false")
    @Default
    private boolean spreadsheetLoadGeneratorRegulatingTerminal = false;

    public SpreadsheetParameters toDto() {
        return new SpreadsheetParameters(
            new BranchSpreadsheetParameters(this.spreadsheetLoadBranchOperationalLimitGroup),
            new BranchSpreadsheetParameters(this.spreadsheetLoadLineOperationalLimitGroup),
            new BranchSpreadsheetParameters(this.spreadsheetLoad2wtOperationalLimitGroup),
            new GeneratorSpreadsheetParameters(this.spreadsheetLoadGeneratorRegulatingTerminal)
        );
    }

    /**
     * @return {@code true} if the update has modified the parameters, {@code false} otherwise.
     */
    public boolean update(@NonNull final SpreadsheetParameters dto) {
        boolean modified = false;
        final BranchSpreadsheetParameters branchParams = dto.getBranch();
        if (branchParams != null) {
            if (branchParams.getOperationalLimitsGroups() != null && this.spreadsheetLoadBranchOperationalLimitGroup != branchParams.getOperationalLimitsGroups()) {
                modified = true;
                this.spreadsheetLoadBranchOperationalLimitGroup = branchParams.getOperationalLimitsGroups();
            }
        }
        final BranchSpreadsheetParameters lineParams = dto.getLine();
        if (lineParams != null) {
            if (lineParams.getOperationalLimitsGroups() != null && this.spreadsheetLoadLineOperationalLimitGroup != lineParams.getOperationalLimitsGroups()) {
                modified = true;
                this.spreadsheetLoadLineOperationalLimitGroup = lineParams.getOperationalLimitsGroups();
            }
        }
        final BranchSpreadsheetParameters t2wParams = dto.getTwoWT();
        if (t2wParams != null) {
            if (t2wParams.getOperationalLimitsGroups() != null && this.spreadsheetLoad2wtOperationalLimitGroup != t2wParams.getOperationalLimitsGroups()) {
                modified = true;
                this.spreadsheetLoad2wtOperationalLimitGroup = t2wParams.getOperationalLimitsGroups();
            }
        }
        final GeneratorSpreadsheetParameters generatorParams = dto.getGenerator();
        if (generatorParams != null) {
            if (generatorParams.getRegulatingTerminal() != null && this.spreadsheetLoadGeneratorRegulatingTerminal != generatorParams.getRegulatingTerminal()) {
                modified = true;
                this.spreadsheetLoadGeneratorRegulatingTerminal = generatorParams.getRegulatingTerminal();
            }
        }
        return modified;
    }
}
