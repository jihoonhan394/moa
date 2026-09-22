package com.moara.moa.category;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/** 기관 스코프 관리형 카테고리(자산/공유자산 등록 시 셀렉트 후보). 자산 관리자가 CRUD 한다. */
@Entity
@Table(name = "categories")
public class Category {
  @Id
  private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private CategoryDomain domain;

  @Column(nullable = false)
  private String name;

  @Column(name = "sort_order", nullable = false)
  private int sortOrder;

  /** 계층: 상위 카테고리(루트는 null). */
  @Column(name = "parent_id")
  private UUID parentId;

  /** ASSET 노드의 유형 귀속(PHYSICAL/SOFTWARE 문자열). SHARED_RESOURCE는 null. */
  @Column(name = "item_type")
  private String itemType;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  protected Category() {}

  public Category(UUID id, UUID tenantId, CategoryDomain domain, String name, int sortOrder,
      UUID parentId, String itemType, OffsetDateTime createdAt) {
    this.id = id;
    this.tenantId = tenantId;
    this.domain = domain;
    this.name = name;
    this.sortOrder = sortOrder;
    this.parentId = parentId;
    this.itemType = itemType;
    this.createdAt = createdAt;
  }

  public void rename(String name) {
    this.name = name;
  }

  public UUID getId() { return id; }
  public UUID getTenantId() { return tenantId; }
  public CategoryDomain getDomain() { return domain; }
  public String getName() { return name; }
  public int getSortOrder() { return sortOrder; }
  public UUID getParentId() { return parentId; }
  public String getItemType() { return itemType; }
  public OffsetDateTime getCreatedAt() { return createdAt; }
}
