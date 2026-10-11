package com.nexora.restopos.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TicketEmailRequest {
    @NotBlank @Email
    private String email;
}
