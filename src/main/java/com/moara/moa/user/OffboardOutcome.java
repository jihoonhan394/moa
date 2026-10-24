package com.moara.moa.user;

/**
 * 퇴사 회수 한 항목의 결과(표시용 라벨 + 건수).
 *
 * <p>{@code pending}이면 <b>요청만 했고 아직 끝나지 않은</b> 항목이다(예: 실물 자산 반납 대기).
 * 권한 삭제처럼 그 자리에서 끝나는 회수와 같은 줄에 "회수 N건"으로 합쳐 보이면, 처리한 사람은
 * 노트북까지 돌려받은 줄 안다 — 실제로는 아직 퇴사자 손에 있는데.
 */
public record OffboardOutcome(String label, long count, boolean pending) {
  /** 그 자리에서 끝난 회수. */
  public OffboardOutcome(String label, long count) {
    this(label, count, false);
  }

  /** 요청만 했고 후속 확인이 남은 회수. */
  public static OffboardOutcome pending(String label, long count) {
    return new OffboardOutcome(label, count, true);
  }
}
