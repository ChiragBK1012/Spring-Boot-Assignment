package com.grid07.api.controller;

import com.grid07.api.entity.Bot;
import com.grid07.api.service.BotService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/bots")
@RequiredArgsConstructor
public class BotController {

    private final BotService botService;

    @PostMapping
    public ResponseEntity<Bot> createBot(@RequestBody CreateBotRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(botService.createBot(req.getName(), req.getPersonaDescription()));
    }

    @GetMapping
    public List<Bot> list() {
        return botService.listAll();
    }

    @GetMapping("/{id}")
    public Bot getById(@PathVariable Long id) {
        return botService.getById(id);
    }

    @Data
    public static class CreateBotRequest {
        private String name;
        private String personaDescription;
    }
}
