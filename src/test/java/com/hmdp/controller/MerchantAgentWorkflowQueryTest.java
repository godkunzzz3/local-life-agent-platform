package com.hmdp.controller;

import com.hmdp.dto.Result;
import com.hmdp.service.AgentWorkflowRecorderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MerchantAgentWorkflowQueryTest {

    @InjectMocks
    private MerchantAgentController controller;

    @Mock
    private AgentWorkflowRecorderService agentWorkflowRecorderService;

    @Test
    void shouldQueryWorkflowRuns() {
        when(agentWorkflowRecorderService.queryRuns(10143L)).thenReturn(Result.ok());

        Result result = controller.queryWorkflowRuns(10143L);

        assertEquals(Boolean.TRUE, result.getSuccess());
    }

    @Test
    void shouldQueryWorkflowSteps() {
        when(agentWorkflowRecorderService.querySteps(1L)).thenReturn(Result.ok());

        Result result = controller.queryWorkflowSteps(1L);

        assertEquals(Boolean.TRUE, result.getSuccess());
    }

    @Test
    void shouldReturnFailureWhenWorkflowRecorderRejectsPermission() {
        when(agentWorkflowRecorderService.queryRuns(10143L)).thenReturn(Result.fail("无权查看该店铺 Workflow"));

        Result result = controller.queryWorkflowRuns(10143L);

        assertEquals(Boolean.FALSE, result.getSuccess());
    }
}
