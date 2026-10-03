package com.moara.moa.inventory.tag;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 자산 관리번호(= QR 스티커에 인쇄되는 코드) 한 장.
 *
 * <p>자산의 컬럼이 아니라 별도 엔티티인 이유는 <b>스티커를 미리 발급해 두고 나중에 붙이기</b>
 * 때문이다. 발급 시점에는 어느 자산에 붙을지 모르므로 {@code itemId} 가 비어 있는 구간이 있다.
 *
 * <pre>
 *   발급(issue)  →  [미부착]  →  부착(attachTo)  →  [부착됨]
 * </pre>
 */
@Entity
@Table(name = "asset_tags")
public class AssetTag {

  @Id
  private String code;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "item_id")
  private UUID itemId;

  @Column(name = "issued_at", nullable = false)
  private OffsetDateTime issuedAt;

  @Column(name = "issued_by")
  private UUID issuedBy;

  @Column(name = "attached_at")
  private OffsetDateTime attachedAt;

  @Column(name = "attached_by")
  private UUID attachedBy;

  protected AssetTag() {}

  private AssetTag(String code, UUID tenantId, OffsetDateTime issuedAt, UUID issuedBy) {
    this.code = code;
    this.tenantId = tenantId;
    this.issuedAt = issuedAt;
    this.issuedBy = issuedBy;
  }

  /** 아직 아무 자산에도 붙지 않은 스티커를 발급한다. */
  public static AssetTag issue(String code, UUID tenantId, OffsetDateTime issuedAt, UUID issuedBy) {
    return new AssetTag(code, tenantId, issuedAt, issuedBy);
  }

  /**
   * 자산에 붙인다.
   *
   * <p>이미 붙은 스티커를 다른 자산으로 옮기지 않는다. 옮기면 과거 기록이 가리키는 대상이
   * 바뀌고, 현장에서는 "같은 번호인데 다른 물건"이 된다.
   */
  public void attachTo(UUID itemId, OffsetDateTime at, UUID by) {
    if (this.itemId != null) {
      throw new AssetTagAlreadyAttachedException(code);
    }
    this.itemId = itemId;
    this.attachedAt = at;
    this.attachedBy = by;
  }

  public boolean isAttached() {
    return itemId != null;
  }

  public String getCode() {
    return code;
  }

  public UUID getTenantId() {
    return tenantId;
  }

  public UUID getItemId() {
    return itemId;
  }

  public OffsetDateTime getIssuedAt() {
    return issuedAt;
  }

  public UUID getIssuedBy() {
    return issuedBy;
  }

  public OffsetDateTime getAttachedAt() {
    return attachedAt;
  }

  public UUID getAttachedBy() {
    return attachedBy;
  }
}
