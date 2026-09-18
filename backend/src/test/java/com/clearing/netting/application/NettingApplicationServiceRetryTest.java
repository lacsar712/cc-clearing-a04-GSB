package com.clearing.netting.application;

import com.clearing.netting.domain.exception.DomainException;
import com.clearing.netting.domain.model.Member;
import com.clearing.netting.domain.model.MemberStatus;
import com.clearing.netting.domain.model.NettingRun;
import com.clearing.netting.domain.model.NettingRunStatus;
import com.clearing.netting.domain.model.ObligationStatus;
import com.clearing.netting.domain.model.TradeObligation;
import com.clearing.netting.domain.port.out.MemberRepositoryPort;
import com.clearing.netting.domain.port.out.NetPositionRepositoryPort;
import com.clearing.netting.domain.port.out.NettingRunRepositoryPort;
import com.clearing.netting.domain.port.out.ObligationRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NettingApplicationServiceRetryTest {

    private NettingRunRepositoryPort runRepository;
    private ObligationRepositoryPort obligationRepository;
    private MemberRepositoryPort memberRepository;
    private NetPositionRepositoryPort positionRepository;
    private NettingRunStatusService statusService;
    private NettingApplicationService service;

    private final LocalDate settleDate = LocalDate.of(2026, 9, 18);

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
        when(obligationRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
        when(positionRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void retryRejectsNonFailedRun() {
        NettingRun completed = run("run-done", NettingRunStatus.COMPLETED, null);
        when(runRepository.findById("run-done")).thenReturn(Optional.of(completed));

        DomainException ex = assertThrows(DomainException.class, () -> service.retry("run-done"));
        assertEquals("INVALID_STATE", ex.getCode());
    }

    @Test
    void retryFailedRunCreatesNewCompletedBatch() {
        NettingRun failed = run("run-failed", NettingRunStatus.FAILED, "suspended member rejected: B");
        when(runRepository.findById("run-failed")).thenReturn(Optional.of(failed));

        TradeObligation o1 = obligation("A", "B", "100");
        TradeObligation o2 = obligation("B", "A", "40");
        when(obligationRepository.findOpenBySettleDateAndCurrency(settleDate, "USD"))
                .thenReturn(List.of(o1, o2));
        when(memberRepository.findByIds(any())).thenReturn(List.of(
                new Member("A", "Bank A", MemberStatus.ACTIVE),
                new Member("B", "Bank B", MemberStatus.ACTIVE)));

        NettingApplicationService.NettingRunResult result = service.retry("run-failed");

        NettingRun newRun = result.run();
        assertNotEquals("run-failed", newRun.getRunId());
        assertEquals(NettingRunStatus.COMPLETED, newRun.getStatus());
        assertEquals(settleDate, newRun.getSettleDate());
        assertEquals("USD", newRun.getCurrency());
        // obligations got linked to the NEW batch
        assertTrue(result.obligations().stream().allMatch(
                o -> o.getStatus() == ObligationStatus.NETTED && newRun.getRunId().equals(o.getNettingRunId())));
        // original failed run untouched (kept as audit record)
        assertEquals(NettingRunStatus.FAILED, failed.getStatus());
    }

    @Test
    void retryStillFailingPersistsNewFailedBatchWithReason() {
        NettingRun failed = run("run-failed", NettingRunStatus.FAILED, "no OPEN obligations");
        when(runRepository.findById("run-failed")).thenReturn(Optional.of(failed));
        when(obligationRepository.findOpenBySettleDateAndCurrency(settleDate, "USD"))
                .thenReturn(List.of());

        DomainException ex = assertThrows(DomainException.class, () -> service.retry("run-failed"));
        assertEquals("NO_OBLIGATIONS", ex.getCode());

        ArgumentCaptor<NettingRun> captor = ArgumentCaptor.forClass(NettingRun.class);
        verify(statusService, atLeastOnce()).saveInNewTx(captor.capture());
        NettingRun lastSaved = captor.getValue();
        assertNotEquals("run-failed", lastSaved.getRunId());
        assertEquals(NettingRunStatus.FAILED, lastSaved.getStatus());
        assertTrue(lastSaved.getFailureReason().contains("no OPEN obligations"));
    }

    private NettingRun run(String runId, NettingRunStatus status, String failureReason) {
        return new NettingRun(runId, settleDate, "USD", status, Instant.now(), failureReason);
    }

    private TradeObligation obligation(String payer, String payee, String amount) {
        return new TradeObligation(
                java.util.UUID.randomUUID().toString(),
                payer,
                payee,
                "USD",
                new BigDecimal(amount),
                settleDate.minusDays(1),
                settleDate,
                ObligationStatus.OPEN,
                null);
    }
}
