package com.moara.moa.solution;

import com.moara.moa.asset.Asset;
import com.moara.moa.asset.AssetService;
import com.moara.moa.credential.CredentialForm;
import com.moara.moa.credential.CredentialService;
import com.moara.moa.credential.CredentialType;
import com.moara.moa.remote.RemoteProtocol;
import com.moara.moa.tenant.Tenant;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

/**
 * 데모용 샘플 솔루션 제어 데이터. {@code moa.sample-data.enabled=true}이고 솔루션이 비어 있을 때만 시드(멱등).
 * 실제 제어가 되도록 test-linux(SSH)/test-windows(WinRM) 자산에 자격증명 + 솔루션을 연결한다.
 * 비밀값은 env로만 주입({@code MOA_SAMPLE_SOLUTION_SECRET}/{@code MOA_SAMPLE_WIN_SECRET}); 미설정 시 해당 항목은 건너뛴다.
 */
@Configuration
@ConditionalOnProperty(name = "moa.sample-data.enabled", havingValue = "true")
public class SampleSolutionDataInitializer {
  private static final Logger log = LoggerFactory.getLogger(SampleSolutionDataInitializer.class);
  private static final UUID TENANT = Tenant.DEFAULT_TENANT_ID;

  @Bean
  @Order(2) // 자산/사용자/그룹 시드(@Order(1)) 이후 실행
  CommandLineRunner sampleSolutions(
      AssetService assetService,
      CredentialService credentialService,
      ManagedSolutionService solutionService,
      ManagedSolutionRepository solutionRepository,
      @Value("${MOA_SAMPLE_SOLUTION_SECRET:}") String linuxSecret,
      @Value("${MOA_SAMPLE_WIN_SECRET:}") String winSecret) {
    return arguments -> {
      if (solutionRepository.count() > 0) {
        return; // 멱등: 이미 솔루션이 있으면 시드하지 않음
      }
      Map<String, UUID> assetIds = assetService.findAll(TENANT).stream()
          .collect(Collectors.toMap(Asset::getName, Asset::getId, (a, b) -> a));

      UUID linuxAsset = assetIds.get("test-linux");
      if (linuxAsset != null && notBlank(linuxSecret)) {
        UUID cred = credentialService.create(TENANT,
            new CredentialForm("linux-mtcm", CredentialType.PASSWORD, "mtcm", linuxSecret)).getId();
        solutionService.create(TENANT, new SolutionForm(
            linuxAsset, "testapp", SolutionType.CUSTOM_COMMAND, "testapp", cred,
            HealthCheckType.TCP_PORT, "18080", SolutionStatus.ACTIVE,
            "bash /home/mtcm/testapp/start.sh", "bash /home/mtcm/testapp/stop.sh",
            "bash /home/mtcm/testapp/status.sh", RemoteProtocol.SSH, null));
        log.info("SAMPLE-SEED: solution testapp (test-linux, SSH/CUSTOM_COMMAND)");
      }

      UUID winAsset = assetIds.get("test-windows");
      if (winAsset != null && notBlank(winSecret)) {
        UUID cred = credentialService.create(TENANT,
            new CredentialForm("win-mtcm", CredentialType.PASSWORD, "MTCM", winSecret)).getId();
        solutionService.create(TENANT, new SolutionForm(
            winAsset, "win-timesvc", SolutionType.WINDOWS_SERVICE, "W32Time", cred,
            HealthCheckType.NONE, null, SolutionStatus.ACTIVE, null, null, null,
            RemoteProtocol.WINRM, null));
        log.info("SAMPLE-SEED: solution win-timesvc (test-windows, WinRM/WINDOWS_SERVICE)");
      }
    };
  }

  private static boolean notBlank(String value) {
    return value != null && !value.isBlank();
  }
}
