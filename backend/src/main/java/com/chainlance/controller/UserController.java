package com.chainlance.controller;

import com.chainlance.dto.UpdateProfileRequest;
import com.chainlance.dto.UserResponse;
import com.chainlance.security.AppUserDetails;
import com.chainlance.service.UserService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal AppUserDetails current) {
        return userService.getById(current.getId());
    }

    @PutMapping("/me")
    public UserResponse updateMe(@AuthenticationPrincipal AppUserDetails current,
                                 @Valid @RequestBody UpdateProfileRequest request) {
        return userService.updateProfile(current.getId(), request);
    }
}
