package com.fintrack.apiservice.user.controller;

import com.fintrack.apiservice.user.dto.FintrackUserCreateRequest;
import com.fintrack.apiservice.user.dto.FintrackUserResponse;
import com.fintrack.apiservice.user.dto.FintrackUserUpdateRequest;
import com.fintrack.apiservice.user.service.FintrackUserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/users")
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
    public ResponseEntity<FintrackUserResponse> getUserById(
            @PathVariable Long id
    ) {

        return ResponseEntity.ok(
                service.getUserById(id)
        );
    }


    @PostMapping
    public ResponseEntity<FintrackUserResponse> createUser(
            @Valid @RequestBody FintrackUserCreateRequest request
    ) {

        FintrackUserResponse response = service.createUser(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<FintrackUserResponse> updateUser(
            @PathVariable Long id,
            @Valid @RequestBody FintrackUserUpdateRequest request
    ) {

        return ResponseEntity.ok(
                service.updateUser(id, request)
        );
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(
            @PathVariable Long id
    ) {

        service.deleteUser(id);

        return ResponseEntity.noContent().build();
    }
}