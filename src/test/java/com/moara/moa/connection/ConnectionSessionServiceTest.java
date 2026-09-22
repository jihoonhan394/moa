package com.moara.moa.connection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.moara.moa.asset.Asset;
import com.moara.moa.asset.AssetForm;
import com.moara.moa.asset.AssetProtocol;
import com.moara.moa.asset.AssetService;
import com.moara.moa.asset.AssetStatus;
import com.moara.moa.asset.AssetType;
import com.moara.moa.tenant.CreateTenantCommand;
import com.moara.moa.tenant.Tenant;
import com.moara.moa.tenant.TenantService;
import com.moara.moa.user.ManagedUser;
import com.moara.moa.user.ManagedUserService;
import com.moara.moa.user.UserForm;
import com.moara.moa.user.UserStatus;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class ConnectionSessionServiceTest {
  @Autowired private ConnectionSessionService sessionService;
  @Autowired private AssetService assetService;
  @Autowired private ManagedUserService userService;
  @Autowired private TenantService tenantService;

  private static final UUID MOA = Tenant.DEFAULT_TENANT_ID;

  @Test
  void recordsFullLifecycleWithAppendOnlyEvents() {
    UUID userId = createMoaUser();
    UUID assetId = createMoaAsset();

    ConnectionSession session = sessionService.openSession(
        MOA, userId, assetId, SessionProtocol.SSH, sessionId(), "203.0.113.9");
    assertEquals(ConnectionStatus.ATTEMPTING, session.getStatus());

    sessionService.markConnected(MOA, session.getId());
    sessionService.markClosed(MOA, session.getId());

    ConnectionSession reloaded = sessionService.findById(MOA, session.getId());
    assertEquals(ConnectionStatus.CLOSED, reloaded.getStatus());
    assertNotNull(reloaded.getStartedAt());
    assertNotNull(reloaded.getEndedAt());

    // 상태 전이마다 이벤트가 append 된다: ATTEMPT → SUCCESS → CLOSED.
    List<ConnectionEvent> events = sessionService.findEvents(MOA, session.getId());
    assertEquals(3, events.size());
    assertEquals(ConnectionEventType.ATTEMPT, events.get(0).getEventType());
    assertEquals(ConnectionEventType.SUCCESS, events.get(1).getEventType());
    assertEquals(ConnectionEventType.CLOSED, events.get(2).getEventType());
  }

  @Test
  void recordsFailedAttempt() {
    UUID userId = createMoaUser();
    UUID assetId = createMoaAsset();

    ConnectionSession session = sessionService.openSession(
        MOA, userId, assetId, SessionProtocol.RDP, sessionId(), null);
    sessionService.markFailed(MOA, session.getId(), "PERMISSION_DENIED", "not permitted");

    ConnectionSession reloaded = sessionService.findById(MOA, session.getId());
    assertEquals(ConnectionStatus.FAILED, reloaded.getStatus());
    assertEquals("PERMISSION_DENIED", reloaded.getFailureCode());

    List<ConnectionEvent> events = sessionService.findEvents(MOA, session.getId());
    assertEquals(2, events.size());
    assertEquals(ConnectionEventType.FAILURE, events.get(1).getEventType());
    assertEquals(ConnectionEventResult.FAILURE, events.get(1).getResult());
  }

  @Test
  void otherTenantCannotAccessOrTransitionSession() {
    UUID userId = createMoaUser();
    UUID assetId = createMoaAsset();
    ConnectionSession session = sessionService.openSession(
        MOA, userId, assetId, SessionProtocol.SSH, sessionId(), null);

    Tenant other = tenantService.createTenant(
        new CreateTenantCommand("Other " + System.nanoTime(), "OTH" + System.nanoTime()));
    UUID otherTenant = other.getId();
    UUID sid = session.getId();

    assertThrows(ConnectionSessionNotFoundException.class, () -> sessionService.findById(otherTenant, sid));
    assertThrows(ConnectionSessionNotFoundException.class, () -> sessionService.markConnected(otherTenant, sid));
    assertThrows(ConnectionSessionNotFoundException.class, () -> sessionService.findEvents(otherTenant, sid));
  }

  private String sessionId() {
    return "sess-" + UUID.randomUUID();
  }

  private UUID createMoaUser() {
    String username = "user" + System.nanoTime();
    ManagedUser user = userService.create(new UserForm(
        username, "홍길동", username + "@example.com", "safe-password-123", UserStatus.ACTIVE));
    return user.getId();
  }

  private UUID createMoaAsset() {
    Asset asset = assetService.create(MOA, new AssetForm("asset-" + System.nanoTime(),
        AssetType.SERVER, AssetProtocol.SSH, "192.0.2.10", 22, "", "LINUX", "test", AssetStatus.ACTIVE));
    return asset.getId();
  }
}
