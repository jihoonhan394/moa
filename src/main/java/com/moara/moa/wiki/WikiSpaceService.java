package com.moara.moa.wiki;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 위키 공간(폴더) 및 그 권한 관리. 접근 판정은 {@link WikiAccessService}가 맡는다. */
@Service
@Transactional(readOnly = true)
public class WikiSpaceService {
  private final WikiSpaceRepository spaceRepository;
  private final WikiSpacePermissionRepository permissionRepository;
  private final WikiPageRepository pageRepository;

  public WikiSpaceService(
      WikiSpaceRepository spaceRepository, WikiSpacePermissionRepository permissionRepository,
      WikiPageRepository pageRepository) {
    this.spaceRepository = spaceRepository;
    this.permissionRepository = permissionRepository;
    this.pageRepository = pageRepository;
  }

  public List<WikiSpace> findAll(UUID tenantId) {
    return spaceRepository.findAllByTenantIdOrderByNameAsc(tenantId);
  }

  public WikiSpace findById(UUID tenantId, UUID id) {
    return spaceRepository.findByTenantIdAndId(tenantId, id)
        .orElseThrow(() -> new WikiSpaceNotFoundException(id));
  }

  /**
   * 특정 사용자에게 <b>직접</b> 부여된 위키 공간 목록('내 워크스페이스' 표시용). 그룹/전체 상속은 제외하고
   * 개인 부여만 본다(온보딩이 부여하는 것도 개인 부여). 삭제된 공간 참조는 걸러낸다.
   */
  public List<WikiSpace> findGrantedToUser(UUID tenantId, UUID userId) {
    return permissionRepository
        .findAllByTenantIdAndSubjectTypeAndSubjectId(tenantId, WikiSubjectType.USER, userId).stream()
        .map(p -> spaceRepository.findByTenantIdAndId(tenantId, p.getSpaceId()).orElse(null))
        .filter(java.util.Objects::nonNull)
        .toList();
  }

  @Transactional
  public WikiSpace create(UUID tenantId, UUID parentId, WikiSpaceForm form) {
    if (parentId != null) {
      findById(tenantId, parentId); // 부모 존재·소유 검증
    }
    return spaceRepository.save(new WikiSpace(UUID.randomUUID(), tenantId, parentId, form, OffsetDateTime.now()));
  }

  @Transactional
  public WikiSpace rename(UUID tenantId, UUID id, WikiSpaceForm form) {
    WikiSpace space = findById(tenantId, id);
    space.rename(form, OffsetDateTime.now());
    return spaceRepository.save(space);
  }

  @Transactional
  public void delete(UUID tenantId, UUID id) {
    WikiSpace space = findById(tenantId, id);
    if (!pageRepository.findAllByTenantIdAndSpaceIdOrderByUpdatedAtDesc(tenantId, id).isEmpty()) {
      throw new WikiSpaceNotEmptyException(id);
    }
    spaceRepository.delete(space); // 하위 폴더·권한은 FK CASCADE
  }

  public List<WikiSpacePermission> permissions(UUID tenantId, UUID spaceId) {
    return permissionRepository.findAllByTenantIdAndSpaceId(tenantId, spaceId);
  }

  /** 권한 부여(있으면 수준만 갱신). ALL은 subjectId=null. */
  @Transactional
  public void grant(
      UUID tenantId, UUID spaceId, WikiSubjectType subjectType, UUID subjectId, WikiAccessLevel level) {
    findById(tenantId, spaceId);
    UUID subject = subjectType == WikiSubjectType.ALL ? null : subjectId;
    permissionRepository.findByTenantIdAndSpaceIdAndSubjectTypeAndSubjectId(tenantId, spaceId, subjectType, subject)
        .ifPresentOrElse(
            existing -> {
              existing.changeLevel(level);
              permissionRepository.save(existing);
            },
            () -> permissionRepository.save(new WikiSpacePermission(
                UUID.randomUUID(), tenantId, spaceId, subjectType, subject, level, OffsetDateTime.now())));
  }

  @Transactional
  public void revoke(UUID tenantId, UUID permissionId) {
    permissionRepository.findByTenantIdAndId(tenantId, permissionId)
        .ifPresent(permissionRepository::delete);
  }
}
