package com.moara.moa.web;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.moara.moa.group.AccessGroup;
import com.moara.moa.group.AccessGroupForm;
import com.moara.moa.group.AccessGroupService;
import com.moara.moa.group.AccessGroupStatus;
import com.moara.moa.security.MoaUserDetails;
import com.moara.moa.user.ManagedUser;
import com.moara.moa.user.ManagedUserService;
import com.moara.moa.user.UserForm;
import com.moara.moa.user.UserStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AccessGroupControllerTest {
  @Autowired private MockMvc mockMvc;
  @Autowired private AccessGroupService groupService;
  @Autowired private ManagedUserService userService;

  @Test
  void createsGroupViaPostThenListsIt() throws Exception {
    ManagedUser admin = createMoaUser();
    String groupName = "그룹-" + System.nanoTime();

    mockMvc.perform(post("/groups").with(authentication(auth(admin))).with(csrf())
            .param("name", groupName).param("description", "설명"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/groups"));

    mockMvc.perform(get("/groups").with(authentication(auth(admin))))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString(groupName)));
  }

  @Test
  void managesMembersFromDetailPage() throws Exception {
    ManagedUser admin = createMoaUser();
    ManagedUser member = createMoaUser();
    AccessGroup group = groupService.create(admin.getTenantId(),
        new AccessGroupForm("grp-" + System.nanoTime(), null, AccessGroupStatus.ACTIVE, null));

    // 멤버 추가
    mockMvc.perform(post("/groups/" + group.getId() + "/members").with(authentication(auth(admin))).with(csrf())
            .param("userId", member.getId().toString()))
        .andExpect(redirectedUrl("/groups/" + group.getId()));
    assertTrue(groupService.findMembers(admin.getTenantId(), group.getId()).stream()
        .anyMatch(m -> m.getUserId().equals(member.getId())));

    // 상세 화면 렌더링(템플릿 컴파일) 확인. 자산 권한은 권한 메뉴로 이관됨(T19).
    mockMvc.perform(get("/groups/" + group.getId()).with(authentication(auth(admin))))
        .andExpect(status().isOk());

    // 제거 후 서비스 상태에서 사라진다
    mockMvc.perform(post("/groups/" + group.getId() + "/members/" + member.getId() + "/delete")
            .with(authentication(auth(admin))).with(csrf()))
        .andExpect(redirectedUrl("/groups/" + group.getId()));
    assertEquals(0, groupService.findMembers(admin.getTenantId(), group.getId()).size());
  }

  private UsernamePasswordAuthenticationToken auth(ManagedUser user) {
    MoaUserDetails principal = new MoaUserDetails(user);
    return new UsernamePasswordAuthenticationToken(principal, "",
        java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_TENANT_ADMIN")));
  }

  private ManagedUser createMoaUser() {
    String username = "user" + System.nanoTime();
    return userService.create(new UserForm(
        username, "회원" + System.nanoTime(), username + "@example.com", "safe-password-123", UserStatus.ACTIVE));
  }
}
