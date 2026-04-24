package com.grid07.api.dto;

import com.grid07.api.entity.Post;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

public class PostDto {

    @Data
    public static class CreateRequest {
        @NotNull
        private Long authorId;

        @NotNull
        private Post.AuthorType authorType;

        @NotBlank
        private String content;
    }

    @Data
    public static class Response {
        private Long id;
        private Long authorId;
        private Post.AuthorType authorType;
        private String content;
        private String createdAt;
        private Long viralityScore;
    }
}
