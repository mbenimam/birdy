package me.birdy.users;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record SignupRequest(
        @NotNull
        @Pattern(regexp = "[A-Za-z0-9_]{3,30}", message = "must be 3 to 30 letters, digits or underscores")
        String username,

        @NotBlank @Email @Size(max = 254) String email,

        @NotNull @Size(min = 8, max = 128) String password,

        @NotNull @Past LocalDate birthday) {
}
