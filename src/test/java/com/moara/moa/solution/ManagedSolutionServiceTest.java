package com.moara.moa.solution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.moara.moa.asset.Asset;
import com.moara.moa.asset.AssetForm;
import com.moara.moa.asset.AssetProtocol;
import com.moara.moa.asset.AssetService;
import com.moara.moa.asset.AssetStatus;
import com.moara.moa.asset.AssetType;
import com.moara.moa.credential.Credential;
import com.moara.moa.credential.CredentialForm;
import com.moara.moa.credential.CredentialService;
import com.moara.moa.credential.CredentialType;
import com.moara.moa.remote.RemoteProtocol;
import com.moara.moa.tenant.Tenant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class ManagedSolutionServiceTest {
  private static final UUID TENANT = Tenant.DEFAULT_TENANT_ID;

  @Autowired private ManagedSolutionService solutionService;
  @Autowired private AssetService assetService;
  @Autowired private CredentialService credentialService;

  @Test
  void createWithAssetAndCredential() {
    Asset server = newServer();
    Credential credential = newCredential();
    ManagedSolution solution = solutionService.create(TENANT, new SolutionForm(
        server.getId(), "tomcat", SolutionType.LINUX_DAEMON, "tomcat.service",
        credential.getId(), HealthCheckType.TCP_PORT, "8080", SolutionStatus.ACTIVE, null, null, null,
        RemoteProtocol.SSH, null));

    ManagedSolution found = solutionService.findById(TENANT, solution.getId());
    assertEquals("tomcat", found.getName());
    assertEquals(credential.getId(), found.getCredentialId());
    assertEquals(SolutionType.LINUX_DAEMON, found.getType());
    assertTrue(solutionService.findByAsset(TENANT, server.getId()).stream()
        .anyMatch(s -> s.getId().equals(solution.getId())));
  }

  @Test
  void createWithoutCredentialAllowed() {
    Asset server = newServer();
    ManagedSolution solution = solutionService.create(TENANT, new SolutionForm(
        server.getId(), "docker-app", SolutionType.DOCKER_CONTAINER, "my-container",
        null, HealthCheckType.NONE, null, SolutionStatus.ACTIVE, null, null, null,
        RemoteProtocol.SSH, null));
    assertNull(solutionService.findById(TENANT, solution.getId()).getCredentialId());
  }

  @Test
  void createCustomCommandStoresCommands() {
    Asset server = newServer();
    ManagedSolution solution = solutionService.create(TENANT, new SolutionForm(
        server.getId(), "myapp", SolutionType.CUSTOM_COMMAND, "myapp",
        null, HealthCheckType.NONE, null, SolutionStatus.ACTIVE,
        "bash start.sh", "bash stop.sh", "bash status.sh", RemoteProtocol.SSH, null));
    ManagedSolution found = solutionService.findById(TENANT, solution.getId());
    assertEquals("bash start.sh", found.getStartCommand());
    assertEquals("bash stop.sh", found.getStopCommand());
    assertEquals("bash status.sh", found.getStatusCommand());
  }

  @Test
  void duplicateNameOnSameAssetRejected() {
    Asset server = newServer();
    SolutionForm form = new SolutionForm(server.getId(), "dup", SolutionType.LINUX_DAEMON, "x.service",
        null, HealthCheckType.NONE, null, SolutionStatus.ACTIVE, null, null, null, RemoteProtocol.SSH, null);
    solutionService.create(TENANT, form);
    assertThrows(DuplicateSolutionException.class, () -> solutionService.create(TENANT, form));
  }

  private Asset newServer() {
    return assetService.create(TENANT, new AssetForm(
        "srv-" + System.nanoTime(), AssetType.SERVER, AssetProtocol.SSH, "100.85.241.103", 22, "", "LINUX", "t",
        AssetStatus.ACTIVE));
  }

  private Credential newCredential() {
    return credentialService.create(TENANT, new CredentialForm(
        "cred-" + System.nanoTime(), CredentialType.PASSWORD, "mtcm", "secret"));
  }
}
