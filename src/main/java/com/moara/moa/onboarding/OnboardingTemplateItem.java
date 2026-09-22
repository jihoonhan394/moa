package com.moara.moa.onboarding;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * 온보딩 템플릿의 구성 항목. 종류에 따라 참조(refId)·문구(label) 사용이 다르다:
 * ASSIGN_SOLUTION/GRANT_WIKI_SPACE는 refId(솔루션·공간 id), TASK/ACK_DOC는 label(표시 문구),
 * ACK_DOC는 refId(위키 공간)도 함께 쓸 수 있다.
 */
@Entity
@Table(name = "onboarding_template_items")
public class OnboardingTemplateItem {
  @Id
  private UUID id;

  @Column(name = "template_id", nullable = false)
  private UUID templateId;

  @Column(name = "item_type")
  @Enumerated(EnumType.STRING)
  private OnboardingItemType itemType;

  @Column(name = "ref_id")
  private UUID refId;

  private String label;

  @Column(name = "sort_order")
  private int sortOrder;

  protected OnboardingTemplateItem() {}

  public OnboardingTemplateItem(
      UUID id, UUID templateId, OnboardingItemType itemType, UUID refId, String label, int sortOrder) {
    this.id = id;
    this.templateId = templateId;
    this.itemType = itemType;
    this.refId = refId;
    this.label = label == null || label.isBlank() ? null : label.trim();
    this.sortOrder = sortOrder;
  }

  public UUID getId() { return id; }
  public UUID getTemplateId() { return templateId; }
  public OnboardingItemType getItemType() { return itemType; }
  public UUID getRefId() { return refId; }
  public String getLabel() { return label; }
  public int getSortOrder() { return sortOrder; }
}
