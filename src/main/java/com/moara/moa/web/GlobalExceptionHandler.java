package com.moara.moa.web;

import com.moara.moa.asset.AssetNotFoundException;
import com.moara.moa.notice.NoticeNotFoundException;
import com.moara.moa.user.ManagedUserNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

@Controller
public class GlobalExceptionHandler {
  @ExceptionHandler({
      AssetNotFoundException.class, ManagedUserNotFoundException.class, NoticeNotFoundException.class})
  @ResponseStatus(HttpStatus.NOT_FOUND)
  public String handleAssetNotFound() {
    return "error/404";
  }
}
