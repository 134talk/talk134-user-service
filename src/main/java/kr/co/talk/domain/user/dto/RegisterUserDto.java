package kr.co.talk.domain.user.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RegisterUserDto {
    private String name;
    private String teamCode;
}
