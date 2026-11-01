package com.yeogido.backend.domain.friend.exception;

import com.yeogido.backend.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum FriendErrorCode implements ErrorCode {

    SELF_FRIEND_REQUEST(
            HttpStatus.BAD_REQUEST,
            "FRIEND4001",
            "자기 자신에게는 친구 요청을 보낼 수 없습니다."
    ),

    FRIEND_REQUEST_RESPOND_FORBIDDEN(
            HttpStatus.FORBIDDEN,
            "FRIEND4031",
            "요청을 받은 사용자만 수락/거절할 수 있습니다."
    ),

    FRIEND_REQUEST_CANCEL_FORBIDDEN(
            HttpStatus.FORBIDDEN,
            "FRIEND4032",
            "요청을 보낸 사용자만 취소할 수 있습니다."
    ),

    FRIEND_CODE_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "FRIEND4041",
            "존재하지 않는 친구코드입니다."
    ),

    FRIEND_REQUEST_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "FRIEND4042",
            "존재하지 않는 친구 요청입니다."
    ),

    NOT_FRIEND(
            HttpStatus.NOT_FOUND,
            "FRIEND4043",
            "친구 관계가 아닙니다."
    ),

    ALREADY_FRIEND(
            HttpStatus.CONFLICT,
            "FRIEND4091",
            "이미 친구입니다."
    ),

    FRIEND_REQUEST_ALREADY_SENT(
            HttpStatus.CONFLICT,
            "FRIEND4092",
            "이미 친구 요청을 보냈습니다."
    ),

    FRIEND_REQUEST_ALREADY_RECEIVED(
            HttpStatus.CONFLICT,
            "FRIEND4093",
            "상대방이 이미 친구 요청을 보냈습니다."
    ),

    FRIEND_REQUEST_ALREADY_PROCESSED(
            HttpStatus.CONFLICT,
            "FRIEND4094",
            "이미 처리된 친구 요청입니다."
    );

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
