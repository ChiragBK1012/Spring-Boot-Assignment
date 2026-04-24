package com.grid07.api.dto;

import com.grid07.api.entity.Comment;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

public class CommentDto {

    @Data
    public static class CreateRequest {
        @NotNull
        private Long authorId;

        @NotNull
        private Comment.AuthorType authorType;

        @NotBlank
        private String content;

        @Min(0)
        private int depthLevel;

        /**
         * Required when authorType == BOT.
         * The human user who owns the post (for cooldown + notification checks).
         */
        private Long postOwnerId;
    }

    @Data
    public static class Response {
        private Long id;
        private Long postId;
        private Long authorId;
        private Comment.AuthorType authorType;
        private String content;
        private int depthLevel;
        private String createdAt;
    }
}
