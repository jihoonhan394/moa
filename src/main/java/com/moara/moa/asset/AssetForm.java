package com.moara.moa.asset;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.AssertTrue;

public record AssetForm(
    @NotBlank @Size(max = 100) String name,
    @NotNull AssetType assetType,
    @NotNull AssetProtocol protocol,
    @Size(max = 255) String host,
    @Min(1) @Max(65535) Integer port,
    @Size(max = 2048) String url,
    @Size(max = 20) String osType,
    @Size(max = 100) String category,
    OsFamily osFamily,
    @Size(max = 100) String cpu,
    @Size(max = 50) String ram,
    @Size(max = 100) String disk,
    @Size(max = 100) String hwModel,
    @Size(max = 1000) String description,
    @NotNull AssetStatus status) {

  /** 카테고리·OS·사양 도입 이전 호출부(테스트·샘플·팀서버) 호환용. 그 필드들은 미지정(null). */
  public AssetForm(
      String name, AssetType assetType, AssetProtocol protocol, String host, Integer port,
      String url, String osType, String description, AssetStatus status) {
    this(name, assetType, protocol, host, port, url, osType, null, null, null, null, null, null,
        description, status);
  }

  @AssertTrue(message = "서버는 호스트/IP와 포트가 필요하고 웹사이트는 URL이 필요합니다.")
  public boolean isConnectionTargetValid() {
    if (assetType == AssetType.SERVER) {
      return host != null && !host.isBlank() && port != null;
    }
    return url != null && !url.isBlank();
  }

  @AssertTrue(message = "자산 종류에 맞는 프로토콜을 선택하세요.")
  public boolean isProtocolValid() {
    if (assetType == null || protocol == null) {
      return true;
    }
    return assetType == AssetType.SERVER
        ? protocol == AssetProtocol.SSH || protocol == AssetProtocol.RDP
        : protocol == AssetProtocol.HTTP || protocol == AssetProtocol.HTTPS;
  }
}
