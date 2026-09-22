package com.moara.moa.asset;

import com.moara.moa.group.AccessGroup;
import com.moara.moa.group.AccessGroupForm;
import com.moara.moa.group.AccessGroupRepository;
import com.moara.moa.group.AccessGroupService;
import com.moara.moa.group.AccessGroupStatus;
import com.moara.moa.permission.PermissionForm;
import com.moara.moa.permission.PermissionProtocol;
import com.moara.moa.permission.PermissionSetService;
import com.moara.moa.permission.PermissionStatus;
import com.moara.moa.tenant.Tenant;
import com.moara.moa.user.ManagedUser;
import com.moara.moa.user.ManagedUserRepository;
import com.moara.moa.user.ManagedUserService;
import com.moara.moa.user.UserForm;
import com.moara.moa.user.UserRole;
import com.moara.moa.user.UserStatus;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

/**
 * 개발/데모용 샘플 데이터. moa.sample-data.enabled=true일 때만 동작하며, 각 단계는 비어 있을 때만 시드한다(멱등).
 * 자산·사용자·그룹·멤버십·자산 접근 권한을 일관된 그래프로 구성해 화면이 실데이터로 채워지게 한다.
 * 서버 접속 자격증명은 시드하지 않는다(접속 시점 입력, 저장 금지).
 */
@Configuration
@ConditionalOnProperty(name = "moa.sample-data.enabled", havingValue = "true")
public class SampleAssetDataInitializer {
  private static final UUID TENANT = Tenant.DEFAULT_TENANT_ID;

  @Bean
  @Order(1) // 솔루션 시드(@Order(2))보다 먼저 자산/사용자/그룹을 만든다
  CommandLineRunner sampleData(
      AssetRepository assetRepository,
      AssetService assetService,
      ManagedUserRepository userRepository,
      ManagedUserService userService,
      AccessGroupRepository groupRepository,
      AccessGroupService groupService,
      PermissionSetService permissionSetService,
      @Value("${MOA_SAMPLE_USER_PASSWORD:}") String samplePassword) {
    return arguments -> {
      if (assetRepository.count() == 0) {
        sampleAssets().forEach(asset -> assetService.create(TENANT, asset));
      }
      if (userRepository.count() == 0) {
        sampleUsers().forEach(user -> userService.create(new UserForm(
            user.username(), user.name(), user.email(), resolvePassword(samplePassword), user.status())));
        // 데모 테넌트에 기관 관리자를 하나 둔다(dev.kim). 이 사용자만 AI 설정·사용자/그룹 관리에 접근 가능.
        // 실 운영에선 이미 사용자가 있어 이 블록이 실행되지 않으므로(count>0) 안전하다.
        userService.findByTenant(TENANT).stream()
            .filter(u -> "dev.kim".equals(u.getUsername()))
            .findFirst()
            .ifPresent(admin -> userService.assignRoles(admin.getId(), Set.of(UserRole.TENANT_ADMIN)));
      }
      if (groupRepository.count() == 0) {
        seedGroups(groupService, permissionSetService, assetService, userService);
      }
    };
  }

  private void seedGroups(
      AccessGroupService groupService,
      PermissionSetService permissionSetService,
      AssetService assetService,
      ManagedUserService userService) {
    Map<String, UUID> assetIds = assetService.findAll(TENANT).stream()
        .collect(Collectors.toMap(Asset::getName, Asset::getId, (a, b) -> a));
    Map<String, UUID> userIds = userService.findByTenant(TENANT).stream()
        .collect(Collectors.toMap(ManagedUser::getUsername, Function.identity()))
        .entrySet().stream()
        .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().getId()));

    UUID infra = createGroup(groupService, "인프라운영팀", "서버 접속 및 운영 담당");
    addMembers(groupService, infra, userIds, "ops.lee", "ops.park");
    UUID infraPerm = createPermission(permissionSetService, "인프라운영팀 접근");
    addEntry(permissionSetService, infraPerm, assetIds, "dev-web-01", PermissionProtocol.SSH);
    addEntry(permissionSetService, infraPerm, assetIds, "dev-db-01", PermissionProtocol.SSH);
    addEntry(permissionSetService, infraPerm, assetIds, "windows-admin-01", PermissionProtocol.RDP);
    addEntry(permissionSetService, infraPerm, assetIds, "test-linux", PermissionProtocol.SSH);
    addEntry(permissionSetService, infraPerm, assetIds, "test-windows", PermissionProtocol.RDP);
    permissionSetService.assignToGroup(TENANT, infraPerm, infra);

    UUID dev = createGroup(groupService, "개발팀", "개발 서버 및 포털 접근");
    addMembers(groupService, dev, userIds, "dev.kim", "dev.choi");
    UUID devPerm = createPermission(permissionSetService, "개발팀 접근");
    addEntry(permissionSetService, devPerm, assetIds, "dev-web-01", PermissionProtocol.SSH);
    addEntry(permissionSetService, devPerm, assetIds, "운영 포털", PermissionProtocol.HTTPS);
    permissionSetService.assignToGroup(TENANT, devPerm, dev);

    UUID security = createGroup(groupService, "정보보안팀", "감사 및 포털 접근");
    addMembers(groupService, security, userIds, "sec.jung");
    UUID securityPerm = createPermission(permissionSetService, "정보보안팀 접근");
    addEntry(permissionSetService, securityPerm, assetIds, "운영 포털", PermissionProtocol.HTTPS);
    permissionSetService.assignToGroup(TENANT, securityPerm, security);
  }

  private UUID createGroup(AccessGroupService groupService, String name, String description) {
    AccessGroup group = groupService.create(TENANT, new AccessGroupForm(name, description, AccessGroupStatus.ACTIVE, null));
    return group.getId();
  }

  private void addMembers(
      AccessGroupService groupService, UUID groupId, Map<String, UUID> userIds, String... usernames) {
    for (String username : usernames) {
      UUID userId = userIds.get(username);
      if (userId != null) {
        groupService.addMember(TENANT, groupId, userId);
      }
    }
  }

  private UUID createPermission(PermissionSetService permissionSetService, String name) {
    return permissionSetService.create(TENANT, new PermissionForm(name, null, PermissionStatus.ACTIVE)).getId();
  }

  private void addEntry(
      PermissionSetService permissionSetService, UUID permissionId, Map<String, UUID> assetIds,
      String assetName, PermissionProtocol action) {
    UUID assetId = assetIds.get(assetName);
    if (assetId != null) {
      permissionSetService.addEntry(TENANT, permissionId, assetId, action);
    }
  }

  /** MOA_SAMPLE_USER_PASSWORD가 설정되면(8자 이상) 로그인 가능한 공통 비밀번호, 아니면 임의값(로그인 불가). */
  private String resolvePassword(String samplePassword) {
    if (samplePassword != null && samplePassword.length() >= 8) {
      return samplePassword;
    }
    return UUID.randomUUID().toString();
  }

  private List<AssetForm> sampleAssets() {
    return List.of(
        server("dev-web-01", "192.0.2.11", 22, AssetProtocol.SSH, "LINUX", AssetStatus.ACTIVE),
        server("dev-db-01", "192.0.2.12", 22, AssetProtocol.SSH, "LINUX", AssetStatus.ACTIVE),
        server("windows-admin-01", "198.51.100.21", 3389, AssetProtocol.RDP, "WINDOWS", AssetStatus.ACTIVE),
        server("batch-worker-01", "198.51.100.22", 22, AssetProtocol.SSH, "LINUX", AssetStatus.DISABLED),
        // 로컬에서 실접속 테스트가 가능한 대상 서버(자격증명은 접속 시 입력).
        server("test-linux", "100.85.241.103", 22, AssetProtocol.SSH, "LINUX", AssetStatus.ACTIVE),
        server("test-windows", "100.101.19.11", 3389, AssetProtocol.RDP, "WINDOWS", AssetStatus.ACTIVE),
        new AssetForm("운영 포털", AssetType.WEBSITE, AssetProtocol.HTTPS, "", null,
            "https://example.com/portal", "", "개발용 웹사이트 자산", AssetStatus.ACTIVE));
  }

  private List<SampleUser> sampleUsers() {
    return List.of(
        new SampleUser("dev.kim", "김개발", "dev.kim@example.com", UserStatus.ACTIVE),
        new SampleUser("dev.choi", "최프론트", "dev.choi@example.com", UserStatus.ACTIVE),
        new SampleUser("ops.lee", "이운영", "ops.lee@example.com", UserStatus.ACTIVE),
        new SampleUser("ops.park", "박서버", "ops.park@example.com", UserStatus.DISABLED),
        new SampleUser("sec.jung", "정보안", "sec.jung@example.com", UserStatus.ACTIVE));
  }

  private AssetForm server(
      String name, String host, int port, AssetProtocol protocol, String osType, AssetStatus status) {
    return new AssetForm(name, AssetType.SERVER, protocol, host, port, "", osType,
        "개발용 샘플 자산", status);
  }

  private record SampleUser(String username, String name, String email, UserStatus status) {}
}
