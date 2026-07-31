package com.fintrack.apiservice.user.controller;

import com.fintrack.apiservice.auth.dto.FintrackUserProfileUpdateRequest;
import com.fintrack.apiservice.user.dto.FintrackUserResponse;
import com.fintrack.apiservice.user.dto.FintrackUserUpdateRequest;
import com.fintrack.apiservice.user.service.FintrackUserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/users")
public class FintrackUserController {

    private final FintrackUserService service;

    public FintrackUserController(FintrackUserService service) {
        this.service = service;
    }


    @GetMapping
    public ResponseEntity<List<FintrackUserResponse>> getUsers() {

        return ResponseEntity.ok(
                service.getAllUsers()
        );
    }


    @GetMapping("/{id}")
    public ResponseEntity<FintrackUserResponse> getUserById(@PathVariable Long id) {

        return ResponseEntity.ok(
                service.getUserById(id)
        );
    }

    @GetMapping("/me")
    public ResponseEntity<FintrackUserResponse> getCurrentUser(Authentication authentication) {
        FintrackUserResponse response =
                service.getUserByUsername(authentication.getName());

        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<FintrackUserResponse> updateUser(@PathVariable Long id,
            @Valid @RequestBody FintrackUserUpdateRequest request
    ) {

        return ResponseEntity.ok(
                service.updateUser(id, request)
        );
    }

    @PutMapping("/me")
    public ResponseEntity<FintrackUserResponse> updateCurrentUser(
            Authentication authentication,
            @Valid @RequestBody
            FintrackUserProfileUpdateRequest request
    ) {
        FintrackUserResponse response =
                service.updateCurrentUser(
                        authentication.getName(),
                        request
                );

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {

        service.deleteUser(id);

        return ResponseEntity.noContent().build();
    }
}