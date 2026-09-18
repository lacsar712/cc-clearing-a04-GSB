package com.clearing.netting.application;

import com.clearing.netting.domain.exception.DomainException;
import com.clearing.netting.domain.model.Member;
import com.clearing.netting.domain.model.MemberStatus;
import com.clearing.netting.domain.model.NetPosition;
import com.clearing.netting.domain.model.NettingRun;
import com.clearing.netting.domain.model.NettingRunStatus;
import com.clearing.netting.domain.model.TradeObligation;
import com.clearing.netting.domain.port.out.MemberRepositoryPort;
import com.clearing.netting.domain.port.out.NetPositionRepositoryPort;
import com.clearing.netting.domain.port.out.NettingRunRepositoryPort;
import com.clearing.netting.domain.port.out.ObligationRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NettingApplicationServiceRetryTest {

    private static final LocalDate SETTLE = LocalDate.of(2026, 9, 18);

    private NettingRunRepositoryPort runRepository;
    private ObligationRepositoryPort obligationRepository;
    private MemberRepositoryPort memberRepository;
    private NetPositionRepositoryPort positionRepository;
    private NettingRunStatusService statusService;
    private NettingApplicationService service;

    @BeforeEach
    void setUp() {
        runRepository = mock(NettingRunRepositoryPort.class);
        obligationRepository = mock(ObligationRepositoryPort.class);
        memberRepository = mock(MemberRepositoryPort.class);
        positionRepository = mock(NetPositionRepositoryPort.class);
        statusService = mock(NettingRunStatusService.class);
        service = new NettingApplicationService(
                runRepository, obligationRepository, memberRepository, positionRepository, statusService);

        when(runRepository.save(any(NettingRun.class))).thenAnswer(inv -> inv.getArgument(0));
        when(statusService.saveInNewTx(any(NettingRun.class))).thenAnswer(inv -> inv.getArgument(0));
        when(positionRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        when(obligationRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void failedRunRecordsReasonAndRetryAfterDataFixCreatesNewCompletedRun() {
        Member a = new Member("A", "Bank A", MemberStatus.ACTIVE);
        Member suspendedB = new Member("B", "Bank B", MemberStatus.SUSPENDED);
        List<TradeObligation> opens = List.of(obligation("A", "B", "100"));

        when(obligationRepository.findOpenBySettleDateAndCurrency(SETTLE, "USD")).thenReturn(opens);
        when(memberRepository.findByIds(anySet())).thenReturn(List.of(a, suspendedB));

        // First attempt: suspended member -> FAILED run persisted with code + reason
        DomainException ex = assertThrows(DomainException.class, () -> service.execute(SETTLE, "USD"));
        assertEquals("SUSPENDED_MEMBER", ex.getCode());

        NettingRun failed = new NettingRun(
                "failed-run-1", SETTLE, "USD", NettingRunStatus.FAILED,
                java.time.Instant.now(), "SUSPENDED_MEMBER", "suspended member rejected: B");
        when(runRepository.findById("failed-run-1")).thenReturn(Optional.of(failed));

        // Data fixed: member B reactivated
        Member activeB = new Member("B", "Bank B", MemberStatus.ACTIVE);
        when(memberRepository.findByIds(anySet())).thenReturn(List.of(a, activeB));

        NettingApplicationService.NettingRunResult result = service.retry("failed-run-1");

        NettingRun newRun = result.run();
        assertNotEquals("failed-run-1", newRun.getRunId());
        assertEquals(NettingRunStatus.COMPLETED, newRun.getStatus());
        assertEquals(SETTLE, newRun.getSettleDate());
        assertEquals("USD", newRun.getCurrency());
        BigDecimal sum = result.positions().stream()
                .map(NetPosition::getNetAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, sum.compareTo(BigDecimal.ZERO));
        // obligations were linked to the NEW run, not the failed one
        assertEquals(newRun.getRunId(), opens.get(0).getNettingRunId());
        verify(positionRepository).saveAll(any());
    }

    @Test
    void retryRejectsNonFailedRun() {
        NettingRun completed = new NettingRun(
                "run-ok", SETTLE, "USD", NettingRunStatus.COMPLETED,
                java.time.Instant.now(), null, null);
        when(runRepository.findById("run-ok")).thenReturn(Optional.of(completed));

        DomainException ex = assertThrows(DomainException.class, () -> service.retry("run-ok"));
        assertEquals("INVALID_STATE", ex.getCode());
    }

    private TradeObligation obligation(String payer, String payee, String amount) {
        return new TradeObligation(
                java.util.UUID.randomUUID().toString(),
                payer, payee, "USD", new BigDecimal(amount),
                SETTLE.minusDays(1), SETTLE,
                com.clearing.netting.domain.model.ObligationStatus.OPEN, null);
    }
}
