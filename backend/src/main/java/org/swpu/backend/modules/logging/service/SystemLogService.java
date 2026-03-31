package org.swpu.backend.modules.logging.service;

import org.swpu.backend.common.api.PageResult;
import org.swpu.backend.modules.logging.dto.SystemLogOverviewQuery;
import org.swpu.backend.modules.logging.dto.SystemLogQuery;
import org.swpu.backend.modules.logging.model.SystemLogCommand;
import org.swpu.backend.modules.logging.vo.SystemLogOverviewVo;
import org.swpu.backend.modules.logging.vo.SystemLogVo;

public interface SystemLogService {
    void record(SystemLogCommand command);

    PageResult<SystemLogVo> listLogs(String bearerToken, SystemLogQuery query);

    SystemLogOverviewVo overview(String bearerToken, SystemLogOverviewQuery query);
}
