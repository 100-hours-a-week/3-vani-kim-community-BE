package com.vani.week4.backend.auth.dto.request;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * @author vani
 * @since 06/12/25
 *
 * 비밀번호 수정을 위한 비밀번호 확인요청용 레코드
 */
public record CheckPasswordRequest(
    @NotBlank(message = "비밀번호는 필수입니다")
    @Size(min = 8, max = 20, message = "비밀번호는 8-20자 사이여야 합니다")
    @Pattern(
            regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[ !\"#$%&'()*+,-./:;<=>?@\\[\\]^_`{|}~])[A-Za-z\\d !\"#$%&'()*+,-./:;<=>?@\\[\\]^_`{|}~]{8,20}$",
            message = "비밀번호는 영문, 숫자, 특수문자를 포함해야 합니다"
    )
    String password

) { }
