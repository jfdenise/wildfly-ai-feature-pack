/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.ai.deployment;

import static org.wildfly.extension.ai.AILogger.ROOT_LOGGER;
import static org.wildfly.extension.ai.Capabilities.CHAT_MODEL_PROVIDER_CAPABILITY;
import static org.wildfly.extension.ai.Capabilities.EMBEDDING_MODEL_PROVIDER_CAPABILITY;
import static org.wildfly.extension.ai.Capabilities.EMBEDDING_STORE_PROVIDER_CAPABILITY;

import jakarta.inject.Named;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.as.server.deployment.annotation.CompositeIndex;
import org.jboss.as.server.deployment.module.ModuleDependency;
import org.jboss.as.server.deployment.module.ModuleSpecification;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.DotName;
import org.jboss.jandex.FieldInfo;
import org.jboss.jandex.Type;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleLoader;
import org.wildfly.extension.ai.Capabilities;

public class AIDependencyProcessor implements DeploymentUnitProcessor {

    public static final String[] OPTIONAL_MODULES = {
        "dev.langchain4j.ollama",
        "dev.langchain4j.openai",
        "dev.langchain4j.mistral-ai",
        "dev.langchain4j.neo4j",
        "dev.langchain4j.weaviate",
        "dev.langchain4j.web-search-engines"
    };
    public static final String[] EXPORTED_MODULES = {
        "dev.langchain4j",
        "io.smallrye.llm",
        "org.wildfly.extension.ai.injection"
    };

    @Override
    public void deploy(DeploymentPhaseContext deploymentPhaseContext) throws DeploymentUnitProcessingException {
        DeploymentUnit deploymentUnit = deploymentPhaseContext.getDeploymentUnit();
        ModuleSpecification moduleSpecification = deploymentUnit.getAttachment(Attachments.MODULE_SPECIFICATION);
        ModuleLoader moduleLoader = Module.getBootModuleLoader();
        for (String module : OPTIONAL_MODULES) {
            moduleSpecification.addSystemDependency(new ModuleDependency(moduleLoader, module, true, false, true, false));
        }
        for (String module : EXPORTED_MODULES) {
            ModuleDependency modDep = new ModuleDependency(moduleLoader, module, false, true, true, false);
            modDep.addImportFilter(s -> s.equals("META-INF"), true);
            moduleSpecification.addSystemDependency(modDep);
        }
        final CompositeIndex index = deploymentUnit.getAttachment(Attachments.COMPOSITE_ANNOTATION_INDEX);
        if (index == null) {
            throw ROOT_LOGGER.unableToResolveAnnotationIndex(deploymentUnit);
        }
        List<AnnotationInstance> annotations = index.getAnnotations(DotName.createSimple(Named.class));
        if (annotations == null || annotations.isEmpty()) {
            return;
        }
        Set<String> requiredChatModels = new HashSet<>();
        Set<String> requiredEmbeddingModels = new HashSet<>();
        Set<String> requiredEmbeddingStores = new HashSet<>();
        Set<String> requiredContentRetrievers = new HashSet<>();
        for (AnnotationInstance annotation : annotations) {
            if (annotation.target().kind() == AnnotationTarget.Kind.FIELD) {
                FieldInfo field = annotation.target().asField();
                if (field.type().kind() == Type.Kind.CLASS) {
                    try {
                        Class fieldClass = Class.forName(field.type().asClassType().name().toString());
                        if (dev.langchain4j.model.chat.ChatLanguageModel.class.isAssignableFrom(fieldClass)) {
                            ROOT_LOGGER.debug("We need the ChatLanguageModel in the class " + field.declaringClass());
                            String chatLanguageModelName = annotation.value().asString();
                            ROOT_LOGGER.debug("We need the ChatLanguageModel called " + chatLanguageModelName);
                            requiredChatModels.add(chatLanguageModelName);
                        } else if (dev.langchain4j.model.embedding.EmbeddingModel.class.isAssignableFrom(fieldClass)) {
                            ROOT_LOGGER.debug("We need the EmbeddingModel in the class " + field.declaringClass());
                            String embeddingModelName = annotation.value().asString();
                            ROOT_LOGGER.debug("We need the EmbeddingModel called " + embeddingModelName);
                            requiredEmbeddingModels.add(embeddingModelName);
                        } else if (dev.langchain4j.store.embedding.EmbeddingStore.class.isAssignableFrom(fieldClass)) {
                            ROOT_LOGGER.debug("We need the EmbeddingStore in the class " + field.declaringClass());
                            String embeddingStoreName = annotation.value().asString();
                            ROOT_LOGGER.debug("We need the EmbeddingStore called " + embeddingStoreName);
                            requiredEmbeddingStores.add(embeddingStoreName);
                        } else if (dev.langchain4j.rag.content.retriever.ContentRetriever.class.isAssignableFrom(fieldClass)) {
                            ROOT_LOGGER.debug("We need the ContentRetriever in the class " + field.declaringClass());
                            String contentRetrieverName = annotation.value().asString();
                            ROOT_LOGGER.debug("We need the ContentRetriever called " + contentRetrieverName);
                            requiredContentRetrievers.add(contentRetrieverName);
                        }
                    } catch (ClassNotFoundException ex) {
                        ROOT_LOGGER.error("Coudln't get the class type for " + field.type().asClassType().name().toString() + " to be able to check what to inject", ex);
                    }
                }
            }
        }
        if (!requiredChatModels.isEmpty() || !requiredEmbeddingModels.isEmpty() || !requiredEmbeddingStores.isEmpty()) {
            if (!requiredChatModels.isEmpty()) {
                for (String chatLanguageModelName : requiredChatModels) {
                    deploymentUnit.addToAttachmentList(AIAttachements.CHAT_MODEL_KEYS, chatLanguageModelName);
                    deploymentPhaseContext.addDeploymentDependency(CHAT_MODEL_PROVIDER_CAPABILITY.getCapabilityServiceName(chatLanguageModelName), AIAttachements.CHAT_MODELS);
                }
            }
            if (!requiredEmbeddingModels.isEmpty()) {
                for (String embeddingModelName : requiredEmbeddingModels) {
                    deploymentUnit.addToAttachmentList(AIAttachements.EMBEDDING_MODEL_KEYS, embeddingModelName);
                    deploymentPhaseContext.addDeploymentDependency(EMBEDDING_MODEL_PROVIDER_CAPABILITY.getCapabilityServiceName(embeddingModelName), AIAttachements.EMBEDDING_MODELS);
                }
            }
            if (!requiredEmbeddingStores.isEmpty()) {
                for (String embeddingStoreName : requiredEmbeddingStores) {
                    deploymentUnit.addToAttachmentList(AIAttachements.EMBEDDING_STORE_KEYS, embeddingStoreName);
                    deploymentPhaseContext.addDeploymentDependency(EMBEDDING_STORE_PROVIDER_CAPABILITY.getCapabilityServiceName(embeddingStoreName), AIAttachements.EMBEDDING_STORES);
                }
            }
            if (!requiredContentRetrievers.isEmpty()) {
                for (String contentRetrieverName : requiredContentRetrievers) {
                    deploymentUnit.addToAttachmentList(AIAttachements.CONTENT_RETRIEVER_KEYS, contentRetrieverName);
                    deploymentPhaseContext.addDeploymentDependency(Capabilities.CONTENT_RETRIEVER_PROVIDER_CAPABILITY.getCapabilityServiceName(contentRetrieverName), AIAttachements.CONTENT_RETRIEVERS);
                }
            }
        }
    }
}
