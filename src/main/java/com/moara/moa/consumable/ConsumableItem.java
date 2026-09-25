package com.moara.moa.consumable;

import com.moara.moa.support.Values;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 소모품 품목. A4용지·음료수처럼 <b>떨어지면 다시 사는</b> 물건이다.
 *
 * <p>개체를 좇지 않는다 — 인벤토리가 시리얼로 1개씩 추적하는 것과 정반대다. 여기서 아는 것은
 * "얼마나 자주 사는가"뿐이고, 지금 몇 개 남았는지는 <b>사람이 창고를 보고 판단한다</b>.
 */
@Entity
@Table(name = "consumable_items")
public class ConsumableItem {
  @Id
  private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(nullable = false, length = 100)
  private String name;

  @Column(length = 100)
  private String category;

  /** 세는 단위(박스·개·팩). 바꾸면 과거 주문량의 뜻이 달라져 소비율이 무의미해진다. */
  @Column(length = 20)
  private String unit;

  /**
   * 담당자가 아는 예상 주문 주기(일). <b>콜드스타트를 메우는 값</b>이다 — 이게 없으면 신규
   * 품목은 주문이 3번 쌓일 때까지 아무 알림도 못 받고, 분기마다 사는 품목이면 9개월이다.
   */
  @Column(name = "cycle_days")
  private Integer cycleDays;

  @Column(name = "owner_group_id")
  private UUID ownerGroupId;

  @Column(nullable = false)
  private boolean active = true;

  @Column(length = 500)
  private String note;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  protected ConsumableItem() {
  }

  public ConsumableItem(UUID id, UUID tenantId, ConsumableItemForm form, OffsetDateTime now) {
    this.id = id;
    this.tenantId = tenantId;
    this.createdAt = now;
    applyMeta(form, now);
  }

  public void applyMeta(ConsumableItemForm form, OffsetDateTime now) {
    this.name = form.name().trim();
    this.category = blankToNull(form.category());
    this.unit = blankToNull(form.unit());
    this.cycleDays = form.cycleDays() != null && form.cycleDays() > 0 ? form.cycleDays() : null;
    this.note = blankToNull(form.note());
    this.updatedAt = now;
  }

  /** 소유팀 지정(알림 수신자 판정). null이면 자산 관리자만 받는다. */
  public void assignOwnerGroup(UUID ownerGroupId, OffsetDateTime now) {
    this.ownerGroupId = ownerGroupId;
    this.updatedAt = now;
  }

  /**
   * 더 이상 쓰지 않는 품목. <b>지우지 않고 끄는</b> 이유는 주문 이력이 딸려 있기 때문이다 —
   * 지우면 "작년에 이걸 얼마나 샀나"에 답할 수 없게 된다.
   */
  public void deactivate(OffsetDateTime now) {
    this.active = false;
    this.updatedAt = now;
  }

  public void activate(OffsetDateTime now) {
    this.active = true;
    this.updatedAt = now;
  }

  /** 예상 주기를 실측값으로 고친다. 담당자가 승인했을 때만 부른다(몰래 바꾸지 않는다). */
  public void changeCycleDays(Integer cycleDays, OffsetDateTime now) {
    this.cycleDays = cycleDays != null && cycleDays > 0 ? cycleDays : null;
    this.updatedAt = now;
  }

  private static String blankToNull(String value) {
    return Values.blankToNull(value);
  }

  public UUID getId() { return id; }

  public UUID getTenantId() { return tenantId; }

  public String getName() { return name; }

  public String getCategory() { return category; }

  public String getUnit() { return unit; }

  /** 알림 문구에 붙일 단위. 안 정했으면 "개". */
  public String unitOrDefault() { return unit == null || unit.isBlank() ? "개" : unit; }

  /** 알림·화면에서 쓰는 이름(레코드 접근자 스타일 호출부와 맞추기 위한 별칭). */
  public String name() { return name; }

  public Integer getCycleDays() { return cycleDays; }

  public UUID getOwnerGroupId() { return ownerGroupId; }

  public boolean isActive() { return active; }

  public String getNote() { return note; }

  public OffsetDateTime getCreatedAt() { return createdAt; }

  public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
