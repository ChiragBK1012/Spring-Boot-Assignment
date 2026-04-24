package com.grid07.api.controller;

import com.grid07.api.dto.CommentDto;
import com.grid07.api.dto.LikeDto;
import com.grid07.api.dto.PostDto;
import com.grid07.api.service.PostService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;

    /**
     * POST /api/posts
     * Create a new post authored by a User or a Bot.
     */
    @PostMapping
    public ResponseEntity<PostDto.Response> createPost(@Valid @RequestBody PostDto.CreateRequest request) {
        PostDto.Response response = postService.createPost(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * POST /api/posts/{postId}/comments
     * Add a comment to a post. Bot comments are subject to guardrails.
     */
    @PostMapping("/{postId}/comments")
    public ResponseEntity<CommentDto.Response> addComment(
            @PathVariable Long postId,
            @Valid @RequestBody CommentDto.CreateRequest request) {
        CommentDto.Response response = postService.addComment(postId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * POST /api/posts/{postId}/like
     * Like a post as a human user. Increments virality score by +20.
     */
    @PostMapping("/{postId}/like")
    public ResponseEntity<Void> likePost(
            @PathVariable Long postId,
            @Valid @RequestBody LikeDto.Request request) {
        postService.likePost(postId, request.getUserId());
        return ResponseEntity.ok().build();
    }
}
