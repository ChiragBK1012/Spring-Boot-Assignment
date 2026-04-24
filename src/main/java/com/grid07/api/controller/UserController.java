package com.grid07.api.controller;

import com.grid07.api.entity.User;
import com.grid07.api.service.UserService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping
    public ResponseEntity<User> createUser(@RequestBody CreateUserRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(userService.createUser(req.getUsername(), req.isPremium()));
    }

    @GetMapping
    public List<User> list() {
        return userService.listAll();
    }

    @GetMapping("/{id}")
    public User getById(@PathVariable Long id) {
        return userService.getById(id);
    }

    @Data
    public static class CreateUserRequest {
        private String username;
        private boolean premium;
    }
}
