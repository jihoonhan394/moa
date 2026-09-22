-- 전화번호는 숫자만 저장(표시할 때 하이픈 포맷). 기존에 하이픈 포함으로 저장된 값을 숫자만으로 정규화한다.
UPDATE managed_users
   SET phone = regexp_replace(phone, '[^0-9]', '', 'g')
 WHERE phone IS NOT NULL
   AND phone <> regexp_replace(phone, '[^0-9]', '', 'g');
