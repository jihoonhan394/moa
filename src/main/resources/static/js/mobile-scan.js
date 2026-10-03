/**
 * 모바일 스캔 화면에서 네이티브 카메라를 부른다.
 *
 * 이 파일은 **앱 안에서만** 동작한다. PC 브라우저에는 window.Capacitor 가 없으므로 아무 일도
 * 하지 않고, 화면은 손으로 번호를 치는 폼 그대로 남는다.
 *
 * 왜 서버가 이 JS 를 들고 있나: 네이티브가 서버를 직접 부르지 않기로 했기 때문이다
 * (moa-mobile doc/11 2번). 카메라는 네이티브가 열고, 읽은 값은 **웹뷰에 돌려주어** 기존 폼이
 * 기존 경로로 제출한다. 그래야 인증·CSRF·감사·기관 스코프가 한 벌로 유지된다.
 */
(function () {
  'use strict';

  var bridge = window.Capacitor;
  var scanner = bridge && bridge.Plugins && bridge.Plugins.BarcodeScanner;
  if (!scanner) {
    return; // 앱이 아니다. 손으로 치는 폼만 쓴다.
  }

  var form = document.querySelector('[data-scan-form]');
  var input = document.getElementById('code');
  var slot = document.querySelector('[data-scan-slot]');
  if (!form || !input || !slot) {
    return;
  }

  var button = document.createElement('button');
  button.type = 'button';
  button.className = 'm-action primary';
  button.innerHTML =
    '<span class="icon">&#9635;</span>' +
    '<span><span class="label">카메라로 찍기</span>' +
    '<span class="sub">QR 을 비추면 바로 열립니다</span></span>';
  slot.appendChild(button);

  var status = document.createElement('p');
  status.className = 'm-lede';
  status.style.marginTop = '12px';
  slot.appendChild(status);

  function say(message) {
    status.textContent = message || '';
  }

  async function ensureModule() {
    // 안드로이드는 스캐너 모듈을 처음 한 번 내려받는다. 없으면 scan() 이 그냥 실패한다.
    if (typeof scanner.isGoogleBarcodeScannerModuleAvailable !== 'function') {
      return true;
    }
    var available = await scanner.isGoogleBarcodeScannerModuleAvailable();
    if (available && available.available) {
      return true;
    }
    say('스캐너를 준비하고 있습니다. 잠시만 기다려 주세요.');
    await scanner.installGoogleBarcodeScannerModule();
    return true;
  }

  button.addEventListener('click', async function () {
    button.disabled = true;
    say('');
    try {
      var permission = await scanner.requestPermissions();
      if (permission && permission.camera !== 'granted' && permission.camera !== 'limited') {
        say('카메라 권한이 없어 찍을 수 없습니다. 설정에서 허용해 주세요.');
        return;
      }
      await ensureModule();
      say('');
      var result = await scanner.scan({ formats: ['QR_CODE'] });
      var first = result && result.barcodes && result.barcodes[0];
      if (!first || !first.rawValue) {
        say('읽지 못했습니다. 다시 시도해 주세요.');
        return;
      }
      input.value = first.rawValue;
      form.submit();
    } catch (error) {
      // 사용자가 취소한 경우도 여기로 온다. 실패 원인을 화면에 쓰지 않는다.
      say('읽지 못했습니다. 번호를 직접 입력할 수도 있습니다.');
    } finally {
      button.disabled = false;
    }
  });
})();
