package kr.co.talk.domain.user.service;

import kr.co.talk.domain.user.dto.AuthTokenDto;
import kr.co.talk.domain.user.dto.LoginDto;
import kr.co.talk.domain.user.model.User;
import kr.co.talk.domain.user.repository.UserRepository;
import kr.co.talk.global.exception.CustomException;
import kr.co.talk.domain.user.repository.AuthTokenRepository;
import kr.co.talk.global.config.jwt.JwtTokenProvider;
import kr.co.talk.domain.user.model.AuthToken;
import kr.co.talk.global.exception.CustomError;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static kr.co.talk.domain.user.model.User.Role.ROLE_ADMIN;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class AuthService {
    private final AuthTokenRepository authTokenRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;

    // 토큰 정보를 db에 저장

    // 기존 user 토큰 정보가 있으면 업데이트
    @Transactional
    public AuthTokenDto createAuthToken(Long userId) {

        //jwt 토큰 발급
        String accessToken = jwtTokenProvider.createAccessToken(String.valueOf(userId));
        String refreshToken = jwtTokenProvider.createRefreshToken();

        //userId로 토큰 정보 조회
        AuthToken authToken = authTokenRepository.findByUserId(userId);

        //기존에 인증 정보 있으면 삭제
        if (authToken != null) {
            authTokenRepository.delete(authToken);
        }

        //redis에 인증 정보 저장
        authTokenRepository.save(new AuthToken(refreshToken, userId));

        return new AuthTokenDto(accessToken, refreshToken);

    }

//    @Transactional
//    public void logout(String accessToken){
//
//    }

    //리프레스 토큰으로 액세스 토큰 재발급
    @Transactional
    public LoginDto tokenRefresh(String refreshToken) {
        //refreshToken 유효성 체크
        jwtTokenProvider.validRefreshToken(refreshToken);

        //refreshToken으로 인증 정보 조회
        //인증 정보 없을시 예외
        AuthToken authToken = authTokenRepository.findById(refreshToken).orElseThrow(
                () -> new CustomException(CustomError.REFRESH_TOKEN_INVALID));

        //jwt 토큰 생성
        String newAccessToken = jwtTokenProvider.createAccessToken(String.valueOf(authToken.getUserId()));
        String newRefreshToken = jwtTokenProvider.createRefreshToken();

        User user = userRepository.findByUserId(authToken.getUserId());

        //기존 인증 정보 삭제 후 새로운 인증 정보 저장
        authTokenRepository.delete(authToken);
        authTokenRepository.save(new AuthToken(newRefreshToken, authToken.getUserId()));

        return LoginDto.builder()
                .userId(user.getUserId())
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .nickname(user.getNickname())
                .teamCode(user.getTeam() != null ? user.getTeam().getTeamCode() : null)
                .admin(user.getRole().equals(ROLE_ADMIN))
                .build();

    }
}