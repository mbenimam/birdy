package me.birdy.posts;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The content is trimmed before it is validated and stored. */
public record CreateCommentRequest(@NotBlank @Size(max = 300) String content) {

    public CreateCommentRequest {
        content = content == null ? null : content.strip();
    }
}
