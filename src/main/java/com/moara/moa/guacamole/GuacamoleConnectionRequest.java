package com.moara.moa.guacamole;

import com.moara.moa.connection.SessionProtocol;

/**
 * Guacamole 1회성 세션 생성 요청. 자격증명은 접속 시점 입력값이며 저장하지 않는다(토큰에만 실림).
 */
public record GuacamoleConnectionRequest(
    SessionProtocol protocol,
    String host,
    int port,
    String username,
    String password,
    String displayName) {}
