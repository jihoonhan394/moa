package com.moara.moa.solution;

import jakarta.validation.constraints.Size;
import java.util.UUID;

/** 솔루션 운영 정보 폼(제어 설정과 분리): 위키 매뉴얼 공간 + 유지보수 업체 연락처 + 로그 수집 명령. */
public record SolutionOpsForm(
    UUID wikiSpaceId,
    @Size(max = 200) String vendorName,
    @Size(max = 300) String vendorContact,
    @Size(max = 1000) String vendorNote,
    @Size(max = 1000) String logCommand) {}
