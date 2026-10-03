package com.moara.moa.inventory.tag;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리번호(QR 스티커) 발급·조회·부착.
 *
 * <p>현장 흐름은 하나다 — <b>찍으면 알아서 갈린다.</b> 사용자가 "조회 모드 / 등록 모드"를 고르지
 * 않는다. 코드가 자산에 붙어 있으면 조회, 안 붙어 있으면 등록이다.
 */
@Service
public class AssetTagService {

  /** 코드가 겹치면 다시 뽑는다. 32^7 이라 실제로는 거의 일어나지 않는다. */
  private static final int MAX_GENERATION_ATTEMPTS = 10;

  private final AssetTagRepository repository;

  public AssetTagService(AssetTagRepository repository) {
    this.repository = repository;
  }

  /**
   * 미부착 스티커를 발급한다. 인쇄해서 나눠 줄 것이므로 보통 한 번에 여러 장이다.
   */
  @Transactional
  public List<AssetTag> issue(UUID tenantId, int count, UUID actorId) {
    if (count < 1 || count > 500) {
      throw new IllegalArgumentException("한 번에 1~500장까지 발급할 수 있습니다.");
    }
    OffsetDateTime now = OffsetDateTime.now();
    List<AssetTag> issued = new ArrayList<>(count);
    for (int index = 0; index < count; index++) {
      issued.add(AssetTag.issue(nextUnusedCode(), tenantId, now, actorId));
    }
    return repository.saveAll(issued);
  }

  /**
   * 찍힌 코드를 우리 기관의 스티커로 해석한다.
   *
   * <p>형식이 틀렸거나, 없는 코드거나, <b>다른 기관의 코드</b>면 모두 {@link
   * AssetTagNotFoundException} 이다. 셋을 구별해 알려 주면 그 코드가 실재하는지가 새어 나간다.
   */
  @Transactional(readOnly = true)
  public AssetTag resolve(UUID tenantId, String rawCode) {
    String code = AssetTagCode.normalize(rawCode);
    if (code == null) {
      throw new AssetTagNotFoundException(String.valueOf(rawCode));
    }
    return repository.findByCode(code)
        .filter(tag -> tag.getTenantId().equals(tenantId))
        .orElseThrow(() -> new AssetTagNotFoundException(code));
  }

  /** 스티커를 자산에 붙인다. */
  @Transactional
  public AssetTag attach(UUID tenantId, String rawCode, UUID itemId, UUID actorId) {
    AssetTag tag = resolve(tenantId, rawCode);
    tag.attachTo(itemId, OffsetDateTime.now(), actorId);
    return repository.save(tag);
  }

  /** 자산에 붙어 있는 관리번호. 자산 화면에서 "이 번호로 찾아 주세요"를 보여 주려고 쓴다. */
  @Transactional(readOnly = true)
  public Optional<String> codeFor(UUID tenantId, UUID itemId) {
    return repository.findByTenantIdAndItemId(tenantId, itemId).map(AssetTag::getCode);
  }

  /**
   * 자산 → 관리번호 묶음 조회.
   *
   * <p>자산마다 {@link #codeFor} 를 부르면 목록 한 번에 질의가 N 번 나간다. 자산 45건이면 45번이고,
   * 자산이 수백 건인 기관에서는 목록 화면이 그대로 느려진다.
   */
  @Transactional(readOnly = true)
  public java.util.Map<UUID, String> codesFor(UUID tenantId, java.util.Collection<UUID> itemIds) {
    if (itemIds.isEmpty()) {
      return java.util.Map.of();
    }
    java.util.Map<UUID, String> codes = new java.util.LinkedHashMap<>();
    for (AssetTag tag : repository.findAllByTenantIdAndItemIdIn(tenantId, itemIds)) {
      codes.put(tag.getItemId(), tag.getCode());
    }
    return codes;
  }

  /** 주어진 자산들 중 이미 번호가 붙은 것. 붙이기 화면에서 중복을 막는다. */
  @Transactional(readOnly = true)
  public java.util.Set<UUID> attachedAmong(UUID tenantId, java.util.Collection<UUID> itemIds) {
    if (itemIds.isEmpty()) {
      return java.util.Set.of();
    }
    return repository.findAllByTenantIdAndItemIdIn(tenantId, itemIds).stream()
        .map(AssetTag::getItemId)
        .collect(java.util.stream.Collectors.toSet());
  }

  @Transactional(readOnly = true)
  public List<AssetTag> unattached(UUID tenantId) {
    return repository.findAllByTenantIdAndItemIdIsNullOrderByIssuedAtDesc(tenantId);
  }

  @Transactional(readOnly = true)
  public long unattachedCount(UUID tenantId) {
    return repository.countByTenantIdAndItemIdIsNull(tenantId);
  }

  private String nextUnusedCode() {
    for (int attempt = 0; attempt < MAX_GENERATION_ATTEMPTS; attempt++) {
      String code = AssetTagCode.generate();
      if (!repository.existsByCode(code)) {
        return code;
      }
    }
    throw new IllegalStateException("관리번호를 만들지 못했습니다. 다시 시도해 주세요.");
  }
}
