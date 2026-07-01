package com.hmdp.agent.skill;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.dto.AgentToolDefinitionDTO;
import com.hmdp.dto.AgentToolExecutionRequestDTO;
import com.hmdp.dto.AgentToolExecutionResultDTO;
import com.hmdp.tool.AgentToolExecutor;
import com.hmdp.tool.AgentToolRegistry;
import com.hmdp.tool.ShopAgentTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentToolExecutorReadonlySkillSecurityTest {

    @Mock
    private AgentToolRegistry agentToolRegistry;
    @Mock
    private ShopAgentTool shopAgentTool;

    private AgentToolExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new AgentToolExecutor();
        ReflectionTestUtils.setField(executor, "agentToolRegistry", agentToolRegistry);
        ReflectionTestUtils.setField(executor, "shopAgentTool", shopAgentTool);
        ReflectionTestUtils.setField(executor, "objectMapper", new ObjectMapper());
    }

    @Test
    void shouldRejectUnknownToolNameWithoutFallback() {
        when(agentToolRegistry.listDefinitions()).thenReturn(Arrays.asList(
                readonlyDefinition("shop_profile_tool")
        ));

        AgentToolExecutionResultDTO result = executor.executeReadonlyTool("unknown_tool",
                new AgentToolExecutionRequestDTO().setShopId(10143L));

        assertFalse(result.getSuccess());
        assertTrue(result.getErrorMsg().contains("工具不存在"));
        verify(shopAgentTool, never()).getShop(10143L);
    }

    @Test
    void shouldRejectWriteToolByRegistryMetadata() {
        when(agentToolRegistry.listDefinitions()).thenReturn(Arrays.asList(
                readonlyDefinition("shop_profile_tool"),
                new AgentToolDefinitionDTO()
                        .setName("voucher_campaign_tool")
                        .setToolType("draft")
                        .setAccessLevel("write")
                        .setWriteDatabase(true)
                        .setRequireMerchantConfirm(true)
        ));

        AgentToolExecutionResultDTO result = executor.executeReadonlyTool("voucher_campaign_tool",
                new AgentToolExecutionRequestDTO().setShopId(10143L));

        assertFalse(result.getSuccess());
        assertTrue(result.getErrorMsg().contains("拒绝非只读工具"));
        verify(shopAgentTool, never()).getShop(10143L);
    }

    @Test
    void shouldRejectModelToolNameCreateCampaignDraft() {
        when(agentToolRegistry.listDefinitions()).thenReturn(Arrays.asList(
                readonlyDefinition("shop_profile_tool"),
                new AgentToolDefinitionDTO()
                        .setName("voucher_campaign_tool")
                        .setModelToolName("createCampaignDraft")
                        .setToolType("draft")
                        .setAccessLevel("write")
                        .setWriteDatabase(true)
                        .setRequireMerchantConfirm(true)
        ));

        AgentToolExecutionResultDTO result = executor.executeReadonlyTool("createCampaignDraft",
                new AgentToolExecutionRequestDTO().setShopId(10143L));

        assertFalse(result.getSuccess());
        assertTrue(result.getErrorMsg().contains("工具不存在"));
        verify(shopAgentTool, never()).getShop(10143L);
    }

    @Test
    void shouldNotFallbackToOperationDiagnosisTool() {
        when(agentToolRegistry.listDefinitions()).thenReturn(Arrays.asList(
                readonlyDefinition("operation_diagnosis_tool")
        ));

        AgentToolExecutionResultDTO result = executor.executeReadonlyTool("operation_diagnosis_tool",
                new AgentToolExecutionRequestDTO().setShopId(10143L));

        assertFalse(result.getSuccess());
        assertTrue(result.getErrorMsg().contains("不支持的只读工具"));
        verify(shopAgentTool, never()).getShop(10143L);
    }

    private AgentToolDefinitionDTO readonlyDefinition(String toolName) {
        return new AgentToolDefinitionDTO()
                .setName(toolName)
                .setToolType("readonly")
                .setAccessLevel("read")
                .setWriteDatabase(false)
                .setRequireMerchantConfirm(false);
    }
}
