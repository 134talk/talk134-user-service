package kr.co.talk.domain.user.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.talk.domain.user.dto.AuthTokenDto;
import kr.co.talk.domain.user.dto.LoginDto;
import kr.co.talk.domain.user.dto.SocialKakaoDto;
import kr.co.talk.domain.user.model.User.Role;
import kr.co.talk.global.exception.CustomException;
import kr.co.talk.domain.user.model.User;
import kr.co.talk.global.exception.CustomError;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import static kr.co.talk.domain.user.model.User.Role.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class SocialKakaoService {

    private final UserService userService;
    private final AuthService authService;
    private final ObjectMapper objectMapper;
//    private final RestTemplate restTemplate;

    @Value("${kakao.url.token}")
    private String tokenUrl;

    @Value("${kakao.url.profile}")
    private String profileUrl;

    @Value("${kakao.url.clientId}")
    private String clientId;

    @Value("${kakao.url.clientSecret}")
    private String clientSecret;

    public LoginDto login(SocialKakaoDto.TokenRequest requestDto) throws Exception {

        //액세슨 토큰 발급
        SocialKakaoDto.TokenResponse tokenResponseDto = getAccessToken(requestDto);

        //액세스 토큰으로 유저 정보 조회
        SocialKakaoDto.UserInfo userInfo = getUserInfo(tokenResponseDto.getAccess_token());

        //유저 정보 없으면 유저 생성 먼저 진행
        User user = userService.findByUserUid(userInfo.getId());
        if(user == null){
            user = userService.createUser(userInfo);
        }
        //jwt 토큰 발급
        //accesstoken userId로 발급
        AuthTokenDto authToken = authService.createAuthToken(user.getUserId());

        return LoginDto.builder()
                .userId(user.getUserId())
                .accessToken(authToken.getAccessToken())
                .refreshToken(authToken.getRefreshToken())
                .nickname(user.getNickname())
                .teamCode(user.getTeam() != null ? user.getTeam().getTeamCode() : null)
                .admin(user.getRole().equals(ROLE_ADMIN))
                .build();
    }

    /**
     * 액세스 토큰 조회
     * **/
    private SocialKakaoDto.TokenResponse getAccessToken(SocialKakaoDto.TokenRequest requestDto) throws Exception {
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.setRequestFactory(new HttpComponentsClientHttpRequestFactory());
        //header
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.add("Accept", "application/json");

        //param
        MultiValueMap<String, String> paramMap = new LinkedMultiValueMap<>();
        paramMap.add("client_id", clientId);
        paramMap.add("redirect_uri", requestDto.getRedirectUri());
        paramMap.add("client_secret", clientSecret);
        paramMap.add("code", requestDto.getCode());
        paramMap.add("grant_type", "authorization_code");

        ResponseEntity<String> responseEntity;

        //http 요청
        try {
            responseEntity = restTemplate.postForEntity(tokenUrl, new HttpEntity<>(paramMap, headers),
                    String.class);
        } catch (HttpClientErrorException e) {
            log.error("",e);
            throw new CustomException(CustomError.AUTH_TOKEN_CREATE_FAIL);
        }

        return objectMapper.readValue(responseEntity.getBody(), SocialKakaoDto.TokenResponse.class);

    }

    /**
     * 액세스 토큰으로 유저 정보 조회
     * **/
    public SocialKakaoDto.UserInfo getUserInfo(String token) throws Exception {
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.setRequestFactory(new HttpComponentsClientHttpRequestFactory());

        //header
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + token);
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.add("Accept", "application/json");

        //param
        MultiValueMap<String, String> paramMap = new LinkedMultiValueMap<>();

        ResponseEntity<String> responseEntity;

        //http 요청
        try {
            responseEntity = restTemplate.postForEntity(profileUrl, new HttpEntity<>(paramMap, headers),
                    String.class);
        } catch (HttpClientErrorException e) {
            log.error("",e);
            throw new CustomException(CustomError.AUTH_TOKEN_CREATE_FAIL);
        }
        return objectMapper.readValue(responseEntity.getBody(), SocialKakaoDto.UserInfo.class);
    }
}