package com.moara.moa.guacamole;

/** Guacamole 세션 생성 결과. 브라우저를 redirectUrl로 보내면 웹 SSH/RDP 화면으로 진입한다. */
public record GuacamoleLaunch(String redirectUrl) {}
