package gdsc.toy_project.global.security;

import gdsc.toy_project.domain.user.entity.RefreshToken;
import gdsc.toy_project.domain.user.repository.RefreshTokenRepository;
import gdsc.toy_project.domain.user.repository.UserRepository;
import gdsc.toy_project.global.security.auth.AuthUserService;
import io.jsonwebtoken.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Calendar;
import java.util.Date;

@RequiredArgsConstructor
@Component
public class JwtTokenProvider {

    @Value("spring.jwt.secret")
    private String secretKey;

    private final long accessTokenValidTime = 1000L * 60 * 60 * 6;
    private final long refreshTokenValidTime = 1000L * 60 * 60 * 24 * 30;

    private final AuthUserService authUserService;
    private final RefreshTokenRepository refreshTokenRepository;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    // Access Token 생성
    public String createAccessToken(String uid){
        return this.createToken(uid, accessTokenValidTime);
    }

    // Refresh Token 생성
    public String createRefreshToken(String uid){
        return this.createToken(uid, refreshTokenValidTime);
    }

    // Create Token
    public String createToken(String uid, long tokenValid){
        Claims claims = Jwts.claims().setSubject(uid); // claims 생성 및 payload 설정

        Date date = new Date();
        return Jwts.builder()
                .setClaims(claims)
                .setIssuedAt(date)
                .setExpiration(new Date(date.getTime() + tokenValid)) //토큰 유효 시간 저장
                .signWith(SignatureAlgorithm.HS256, secretKey) // 해싱 알고리즘 및 키 설정
                .compact(); // 생성
    }

    // JWT에서 인증 정보 조회
    public Authentication getAuthentication(String token){
        UserDetails userDetails = authUserService.loadUserByUsername(this.getUid(token));
        return new UsernamePasswordAuthenticationToken(userDetails, "", userDetails.getAuthorities());
    }

    // 토큰에서 회원 정보 추출 - Uid
    public String getUid(String token){
        return Jwts.parser().setSigningKey(secretKey).parseClaimsJws(token).getBody().getSubject();
    }

    // 토큰에서 회원 정보 추출 - Id
    public Long getId(String token){
        return userRepository.findByUid(this.getUid(token)).get().getId();
    }

    // Request의 Header에서 AccessToken 값을 가져옵니다. "Authorization" : "token"
    public String resolveAccessToken(HttpServletRequest request){
        if (request.getHeader("Authorization") != null){
            return request.getHeader("Authorization").substring(7);
        }
        return null;
    }

    // Request의 Header에서 RefreshToken 값을 가져옵니다. "RefreshToken" : "token"
    public String resolveRefreshToken(HttpServletRequest request){
        if (request.getHeader("RefreshToken") != null){
            return request.getHeader("RefreshToken").substring(7);
        }
        return null;
    }

    // 토큰의 유효성 + 만료일자 확인
    public boolean validateToken(String jwtToken) {
        try {
            Jws<Claims> claims = Jwts.parser().setSigningKey(secretKey).parseClaimsJws(jwtToken);
            return !claims.getBody().getExpiration().before(new Date());
        } catch (ExpiredJwtException e) {
            return false;
        }
    }

    // AccsessToken 헤더 설정
    public void setHeaderAccessToken(HttpServletResponse response, String accessToken) {
        response.setHeader("Authorization", "bearer "+ accessToken);
    }

    // RefreshToken 헤더 설정
    public void setHeaderRefreshToken(HttpServletResponse response, String refreshToken) {
        response.setHeader("RefreshToken", "bearer "+ refreshToken);
    }

    // RefreshToken 존재유무 확인
    public boolean existsRefreshToken(String refreshToken) {
        return refreshTokenRepository.existsByRefreshToken(refreshToken);
    }

    public boolean isTokenPeriodLeftEnough (String jwtToken) {
        Date date = new Date();

        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        cal.add(Calendar.DATE, 15);

        date = cal.getTime();

        try {
            Jws<Claims> claims = Jwts.parser().setSigningKey(secretKey).parseClaimsJws(jwtToken);
            return !claims.getBody().getExpiration().before(date);
        } catch (ExpiredJwtException e) {
            return false;
        }
    }

    // RefreshToken update하기
    public void updateRefreshToken(String originRefreshToken, String newRefreshToken) {
        refreshTokenRepository.deleteByRefreshToken(originRefreshToken);
        refreshTokenRepository.save(new RefreshToken(newRefreshToken));
    }
}
