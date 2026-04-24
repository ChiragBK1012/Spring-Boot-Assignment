package com.grid07.api.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

public class LikeDto {

    @Data
    public static class Request {
        @NotNull
        private Long userId;
    }
}
