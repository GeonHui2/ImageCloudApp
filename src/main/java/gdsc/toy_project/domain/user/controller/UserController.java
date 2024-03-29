package gdsc.toy_project.domain.user.controller;

import gdsc.toy_project.domain.user.dto.request.SignInDto;
import gdsc.toy_project.domain.user.dto.request.SignUpDto;
import gdsc.toy_project.domain.user.entity.User;
import gdsc.toy_project.domain.user.service.UserService;
import gdsc.toy_project.global.response.DefaultRes;
import gdsc.toy_project.global.response.StatusCode;
import gdsc.toy_project.global.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.util.Optional;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/user")
public class UserController {

    private final UserService userService;
    private final JwtTokenProvider jwtTokenProvider;

    //아이디 중복 확인
    @GetMapping("/checkUid")
    public ResponseEntity checkUid(@RequestParam(name = "uid") String uid) {
        Boolean result = userService.checkUid(uid);

        return result ?
                new ResponseEntity(DefaultRes.res(StatusCode.OK, "사용가능한 아이디입니다."), HttpStatus.OK) :
                new ResponseEntity(DefaultRes.res(StatusCode.BAD_REQUEST, "중복된 아이디입니다."), HttpStatus.BAD_REQUEST);
    }

    //회원가입 요청
    @PostMapping("/signUp")
    public ResponseEntity signUp(@RequestBody SignUpDto user) {
        Long result = userService.signUp(user);

        return result != null ?
                new ResponseEntity(DefaultRes.res(StatusCode.OK, "회원가입 성공하였습니다."), HttpStatus.OK) :
                new ResponseEntity(DefaultRes.res(StatusCode.BAD_REQUEST, "잘못된 요청입니다."), HttpStatus.BAD_REQUEST);
    }

    //로그인 요청
    @PostMapping("/signIn")
    public ResponseEntity signIn(@RequestBody SignInDto user, HttpServletResponse response) {

        Optional<User> member = userService.findUserByUid(user.getUid());

        if (member.isEmpty()) return new ResponseEntity(DefaultRes.res(StatusCode.BAD_REQUEST, "로그인 아이디 또는 비밀번호 오류입니다"), HttpStatus.BAD_REQUEST);

        boolean checkPassword = userService.checkPassword(user, member.get());

        if (!checkPassword) return new ResponseEntity(DefaultRes.res(StatusCode.BAD_REQUEST, "로그인 아이디 또는 비밀번호 오류입니다"), HttpStatus.BAD_REQUEST);


        //액세스, 리프레시 토큰 발급 및 헤더 설정
        String accessToken = jwtTokenProvider.createAccessToken(member.get().getUid());
        String refreshToken = jwtTokenProvider.createRefreshToken(member.get().getUid());

        jwtTokenProvider.setHeaderAccessToken(response, accessToken);
        jwtTokenProvider.setHeaderRefreshToken(response, refreshToken);

        //리프레시 토큰 저장소에 저장
        userService.signIn(refreshToken, member.get());

        return new ResponseEntity(DefaultRes.res(StatusCode.OK, "로그인 완료"), HttpStatus.OK);
    }

    //로그아웃
    @PostMapping("/signOut")
    public ResponseEntity signOut(@RequestHeader("RefreshToken") String refreshToken, @RequestParam(name = "userUid") String userUid) {
        refreshToken = refreshToken.substring(7);
        User member = userService.findUserByUid(userUid).get();
        Boolean existAndOut = userService.signOut(refreshToken, member);

        return existAndOut ?
                new ResponseEntity(DefaultRes.res(StatusCode.OK, "로그아웃 완료"), HttpStatus.OK) :
                new ResponseEntity(DefaultRes.res(StatusCode.BAD_REQUEST, "잘못된 요청"), HttpStatus.BAD_REQUEST);
    }

    // 통합 예외 핸들러
    @ExceptionHandler
    public String exceptionHandler(Exception exception) {
        return exception.getMessage();
    }
}
