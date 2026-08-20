package com.example.driversettlementsystem.settlement.client;

import java.time.LocalDate;
import java.util.List;

import com.example.driversettlementsystem.exception.ExternalServiceException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 결제 서버 호출을 담당하는 유일한 클래스. (대사 전용)
 * <p>
 * <b>정산에서 결제 서버의 존재를 아는 곳은 여기뿐이다.</b> 결제 API가 바뀌면 고칠 파일이
 * 이것 하나다.
 * <p>
 * ⚠️ 배치는 이 클래스를 쓰지 않는다. 운임 합계의 출처는 원장이다. <b>여기서 받은 값으로
 * 정산 금액을 계산하면 안 된다</b> — 그러면 대사의 두 비교 대상이 같은 출처가 되어 검증이
 * 무의미해진다. 결제 서버는 <b>독립된 두 번째 출처</b>로만 쓴다.
 * <p>
 * 🚧 <b>결제 서버에 이 API가 아직 없다.</b> 계약서 §1 6번에 정산 → 결제 호출로 명시돼 있으나
 * 제공자가 구현 전이다. 계약서 기준으로 만들어 두고, 결제가 API를 내면 그때 붙인다.
 */
@Component
public class PaymentClient
{

    private final RestClient restClient;

    /**
     * @param restClient {@code RestClient} 빈이 결제·원장 2개라 이름으로 구분해야 한다.
     *                   {@code @Qualifier}가 없으면 "빈이 2개인데 어느 것을 쓸지 모르겠다"는
     *                   부팅 에러가 난다
     */
    public PaymentClient(@Qualifier("paymentRestClient") RestClient restClient)
    {
        this.restClient = restClient;
    }

    /**
     * 지정한 날짜의 결제 내역을 모두 가져온다.
     * <p>
     * {@code date}의 기준은 <b>결제 승인 시각</b>이다. 원장도 같은 기준으로 맞추기로 확정했다.
     * 세 서버의 해석이 하나라도 다르면 자정 근처 결제가 서로 다른 날로 잡혀, 계산이 전부
     * 정상인데도 대사가 항상 불일치로 뜬다.
     * <p>
     * 4xx·5xx·타임아웃을 모두 {@link ExternalServiceException}으로 바꾼다. 안 잡으면
     * {@code RestClientException}이 그대로 올라가 공통 에러 포맷을 벗어난다. <b>재시도는 하지
     * 않는다</b> — 실패를 어떻게 처리할지는 대사가 정한다.
     *
     * @param date 조회할 날짜 ({@code yyyy-MM-dd}로 직렬화된다)
     * @return 해당 날짜의 결제 목록. 없으면 빈 목록 ({@code null}이 아니다)
     * @throws ExternalServiceException 결제 서버가 실패하거나 응답하지 않을 때
     */
    public List<PaymentSummary> findPaymentsByDate(LocalDate date)
    {
        PaymentListResponse response;

        try
        {
            response = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/api/payments")
                            .queryParam("date", date)
                            .build())
                    .retrieve()
                    .body(PaymentListResponse.class);
        }
        catch (RestClientException e)
        {
            throw ExternalServiceException.payment(date + " 결제 목록 조회 실패", e);
        }

        if (response == null || response.payments() == null)
        {
            return List.of();
        }

        return response.payments();
    }

    /**
     * 🚧 상대가 배열을 감싸는지 미확정이다. 감싼다고 가정했으며, 아니면 이 레코드만 지우고
     * {@code body(new ParameterizedTypeReference<List<PaymentSummary>>() {})}로 바꾸면 된다.
     * <b>틀릴 수 있는 부분을 한 줄에 가둬 두는 것이 목적이다.</b>
     *
     * @param payments 결제 목록
     */
    private record PaymentListResponse(List<PaymentSummary> payments)
    {
    }

}
