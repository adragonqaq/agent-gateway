package com.agent.gateway.runtime.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 网关 Agent 运行配置。
 * <p>所属模块：runtime。绑定 {@code gateway.agent}，选择 echo 或 agentscope 及模型参数。
 */
@ConfigurationProperties(prefix = "gateway.agent")
public class GatewayAgentProperties {

    /** 运行模式：echo 或 agentscope。 */
    private String mode = "echo";
    private String persona = "You are Agent Gateway, a helpful multi-tenant tool-using assistant.";
    private int maxIters = 8;
    private int maxRetries = 2;
    private Chat chat = new Chat();

    /**
     * @return 运行模式，echo 或 agentscope
     */
    public String getMode() {
        return mode;
    }

    /**
     * @param mode 运行模式
     */
    public void setMode(String mode) {
        this.mode = mode;
    }

    /**
     * @return 系统人设提示词
     */
    public String getPersona() {
        return persona;
    }

    /**
     * @param persona 系统人设提示词
     */
    public void setPersona(String persona) {
        this.persona = persona;
    }

    /**
     * @return ReAct 最大迭代次数
     */
    public int getMaxIters() {
        return maxIters;
    }

    /**
     * @param maxIters ReAct 最大迭代次数
     */
    public void setMaxIters(int maxIters) {
        this.maxIters = maxIters;
    }

    /**
     * @return 模型调用失败时的最大重试次数
     */
    public int getMaxRetries() {
        return maxRetries;
    }

    /**
     * @param maxRetries 最大重试次数
     */
    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    /**
     * @return 聊天模型连接配置
     */
    public Chat getChat() {
        return chat;
    }

    /**
     * @param chat 聊天模型连接配置
     */
    public void setChat(Chat chat) {
        this.chat = chat;
    }

    /**
     * OpenAI 兼容聊天端点配置。
     * <p>所属模块：runtime。对应 {@code gateway.agent.chat}。
     */
    public static class Chat {
        private String apiKey = "";
        private String baseUrl = "https://api.openai.com";
        private String endpointPath = "/v1/chat/completions";
        private String model = "gpt-4o-mini";
        private boolean stream = true;

        /**
         * @return API 密钥；空串表示未配置
         */
        public String getApiKey() {
            return apiKey;
        }

        /**
         * @param apiKey API 密钥
         */
        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        /**
         * @return 模型服务根地址
         */
        public String getBaseUrl() {
            return baseUrl;
        }

        /**
         * @param baseUrl 模型服务根地址
         */
        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        /**
         * @return 聊天补全路径
         */
        public String getEndpointPath() {
            return endpointPath;
        }

        /**
         * @param endpointPath 聊天补全路径
         */
        public void setEndpointPath(String endpointPath) {
            this.endpointPath = endpointPath;
        }

        /**
         * @return 模型名称
         */
        public String getModel() {
            return model;
        }

        /**
         * @param model 模型名称
         */
        public void setModel(String model) {
            this.model = model;
        }

        /**
         * @return 是否启用流式输出
         */
        public boolean isStream() {
            return stream;
        }

        /**
         * @param stream 是否启用流式输出
         */
        public void setStream(boolean stream) {
            this.stream = stream;
        }
    }
}
