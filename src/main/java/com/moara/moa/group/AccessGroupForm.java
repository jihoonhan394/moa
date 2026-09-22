package com.moara.moa.group;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** 그룹 생성·수정 폼. parentId=상위 그룹(조직 트리), NULL이면 최상위. */
public record AccessGroupForm(
    @NotBlank @Size(max = 100) String name,
    @Size(max = 1000) String description,
    AccessGroupStatus status,
    UUID parentId) {}
