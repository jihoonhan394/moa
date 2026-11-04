package com.moara.moa.user;

/** 아이디가 {@link UserForm#USERNAME_PATTERN}을 어겼다(주로 이메일을 아이디 칸에 넣은 경우). */
public class InvalidUsernameException extends IllegalArgumentException {
  public InvalidUsernameException() {
    super("아이디에는 @를 쓸 수 없습니다(이메일은 이메일 칸에).");
  }
}
