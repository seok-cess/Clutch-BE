package com.clutch.coupon.test.event.service;

import com.clutch.coupon.contract.trigger.CouponTestMatch;
import com.clutch.coupon.test.event.api.dto.SampleFrameRequest;
import com.clutch.lolesports.dto.external.WindowResponse;
import com.clutch.lolesports.service.FirstBloodDetector;
import com.clutch.lolesports.service.PentakillDetector;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * 시연 화면의 프레임을 실제 감지기에 흘려보낸다.
 *
 * <p>화면이 "펜타킬이 났다"고 지목하면 감지 로직은 한 줄도 검증되지 않는다.
 * 그래서 화면은 킬 수만 보내고, 판정은 폴링이 쓰는 것과 같은 감지기가 하도록 한다.
 * 트리거가 늘면 여기에도 함께 걸어야 시연에서 같은 경로를 탄다.</p>
 *
 * <p>경기는 예약된 테스트 경기로 고정한다. 요청이 경기를 고르게 두면 시연 화면을
 * 여는 것만으로 실제 경기의 쿠폰을 열 수 있다.</p>
 */
@Service
@RequiredArgsConstructor
public class SampleFrameService {

    /**
     * 합성 프레임 시각의 기준점.
     *
     * <p>감지기의 시간창은 프레임 시각으로 판정한다. 실제 벽시계를 쓰면 배속에 따라
     * 같은 킬이 붙었다 떨어졌다 해서 판정이 배속에 좌우된다. 게임 내 경과 초를
     * 그대로 시각으로 환산해 배속과 무관하게 같은 결과가 나오게 한다.</p>
     */
    private static final Instant FRAME_TIME_BASE = Instant.EPOCH;

    private final PentakillDetector pentakillDetector;
    private final FirstBloodDetector firstBloodDetector;

    /** 프레임 한 장을 감지기에 전달한다. 트리거 발동 여부는 감지기가 판단한다. */
    public void submit(SampleFrameRequest request) {
        Instant frameAt = FRAME_TIME_BASE.plusSeconds(request.gameTimeSeconds());

        WindowResponse.Frame frame = new WindowResponse.Frame(
                frameAt.toString(),
                "in_game",
                teamFrame(request.blue()),
                teamFrame(request.red()),
                request.gameTimeSeconds().longValue()
        );

        pentakillDetector.onNewWindowFrame(
                CouponTestMatch.SAMPLE_EXTERNAL_MATCH_ID,
                request.gameId(),
                frame,
                FRAME_TIME_BASE
        );
        firstBloodDetector.onNewWindowFrame(
                CouponTestMatch.SAMPLE_EXTERNAL_MATCH_ID,
                request.gameId(),
                frame,
                FRAME_TIME_BASE
        );
    }

    /**
     * 시연을 처음부터 다시 재생할 때 이전 회차의 감지 상태를 버린다.
     *
     * <p>상태를 남기면 누적 킬이 줄어든 것으로 보여 증가분이 잡히지 않고,
     * 이미 발동한 참가자로 기록돼 다음 바퀴에서 영영 발동하지 않는다.</p>
     */
    public void reset(String gameId) {
        pentakillDetector.clearGame(gameId);
        firstBloodDetector.clearGame(gameId);
    }

    private WindowResponse.TeamFrame teamFrame(
            List<SampleFrameRequest.Participant> participants
    ) {
        List<SampleFrameRequest.Participant> source =
                participants == null ? List.of() : participants;

        // 팀 누적 킬을 채운다. 참가자 킬만 보내면 팀 집계를 보는 감지기(첫 킬)가
        // 판정 근거가 없어 그대로 넘어간다
        int totalKills = source.stream()
                .map(SampleFrameRequest.Participant::kills)
                .filter(java.util.Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();

        return new WindowResponse.TeamFrame(
                null,
                null,
                null,
                null,
                totalKills,
                List.of(),
                source.stream()
                        .map(participant -> new WindowResponse.ParticipantFrame(
                                participant.participantId(),
                                null,
                                null,
                                participant.kills(),
                                null,
                                null,
                                null,
                                null,
                                null
                        ))
                        .toList()
        );
    }
}
