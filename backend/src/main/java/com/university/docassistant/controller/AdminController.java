package com.university.docassistant.controller;

import com.university.docassistant.dto.AdminDtos.AdminStatsResponse;
import com.university.docassistant.dto.AdminDtos.AdminUserResponse;
import com.university.docassistant.dto.AdminDtos.AuditLogResponse;
import com.university.docassistant.dto.PageResponse;
import com.university.docassistant.dto.AdminDtos.UpdateRoleRequest;
import com.university.docassistant.service.AdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    @GetMapping("/users")
    public List<AdminUserResponse> users() {
        return adminService.listUsers();
    }

    @PatchMapping("/users/{id}/role")
    public AdminUserResponse updateRole(@PathVariable Long id,
                                        @Valid @RequestBody UpdateRoleRequest request,
                                        @AuthenticationPrincipal UserDetails principal) {
        return adminService.updateRole(id, request.role(), principal.getUsername());
    }

    @DeleteMapping("/users/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id, @AuthenticationPrincipal UserDetails principal) {
        adminService.deleteUser(id, principal.getUsername());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/stats")
    public AdminStatsResponse stats() {
        return adminService.stats();
    }

    /** System log, newest first. */
    @GetMapping("/logs")
    public PageResponse<AuditLogResponse> logs(@RequestParam(value = "page", defaultValue = "0") int page,
                                               @RequestParam(value = "size", defaultValue = "50") int size) {
        return adminService.logs(PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, 100)),
                Sort.by(Sort.Direction.DESC, "createdAt", "id")));
    }
}
