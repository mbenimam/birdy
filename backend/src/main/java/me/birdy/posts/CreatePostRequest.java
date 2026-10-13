package me.birdy.posts;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The content is trimmed before it is validated and stored. */
public record CreatePostRequest(@NotBlank @Size(max = 500) String content) {

    public CreatePostRequest {
        content = content == null ? null : content.strip();
    }
}
