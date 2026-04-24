package com.grid07.api.service;

import com.grid07.api.entity.Bot;
import com.grid07.api.repository.BotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BotService {

    private final BotRepository botRepository;

    @Transactional
    public Bot createBot(String name, String personaDescription) {
        Bot bot = Bot.builder()
                .name(name)
                .personaDescription(personaDescription)
                .build();
        return botRepository.save(bot);
    }

    public List<Bot> listAll() {
        return botRepository.findAll();
    }

    public Bot getById(Long id) {
        return botRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Bot not found: " + id));
    }
}
