/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.ai.embeddings;

import static org.wildfly.extension.ai.Capabilities.EMBEDDING_MODEL_PROVIDER_CAPABILITY;
import static org.wildfly.extension.ai.chat.OllamaChatLanguageModelProviderRegistrar.CONNECT_TIMEOUT;
import static org.wildfly.extension.ai.chat.OllamaChatLanguageModelProviderRegistrar.MODEL_NAME;
import static org.wildfly.extension.ai.chat.OpenAIChatLanguageModelProviderRegistrar.BASE_URL;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import java.time.Duration;
import java.util.function.Supplier;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.dmr.ModelNode;
import org.wildfly.subsystem.service.ResourceServiceConfigurator;
import org.wildfly.subsystem.service.ResourceServiceInstaller;
import org.wildfly.subsystem.service.capability.CapabilityServiceInstaller;

/**
 * Configures an aggregate EmbeddingModel provider service.
 */
public class OllamaEmbeddingModelProviderServiceConfigurator implements ResourceServiceConfigurator {

    @Override
    public ResourceServiceInstaller configure(OperationContext context, ModelNode model) throws OperationFailedException {
        String baseUrl = BASE_URL.resolveModelAttribute(context, model).asStringOrNull();
        String modelName = MODEL_NAME.resolveModelAttribute(context, model).asStringOrNull();
        long connectTimeOut = CONNECT_TIMEOUT.resolveModelAttribute(context, model).asLong();
        Supplier<EmbeddingModel> factory = new Supplier<>() {
            @Override
            public EmbeddingModel get() {
                return OllamaEmbeddingModel.builder().baseUrl(baseUrl).modelName(modelName).timeout(Duration.ofMillis(connectTimeOut)).build();
            }
        };
        return CapabilityServiceInstaller.builder(EMBEDDING_MODEL_PROVIDER_CAPABILITY, factory).asActive().build();
    }
}
