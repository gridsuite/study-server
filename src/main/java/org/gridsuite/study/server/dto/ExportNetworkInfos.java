package org.gridsuite.study.server.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * @author Abdelsalem Hedhili <abdelsalem.hedhili at rte-france.com>
 */

@AllArgsConstructor
@NoArgsConstructor
@Getter
public class ExportNetworkInfos {

    private String fileName;

    private byte[] networkData;

}
