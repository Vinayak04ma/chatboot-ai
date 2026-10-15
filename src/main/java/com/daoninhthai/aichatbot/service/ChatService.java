package com.daoninhthai.aichatbot.service;

import com.daoninhthai.aichatbot.dto.request.ChatMessageRequest;
import com.daoninhthai.aichatbot.dto.response.ChatMessageResponse;
import com.daoninhthai.aichatbot.entity.Conversation;
import com.daoninhthai.aichatbot.entity.Message;
import com.daoninhthai.aichatbot.entity.User;
import com.daoninhthai.aichatbot.exception.ResourceNotFoundException;
import com.daoninhthai.aichatbot.repository.ConversationRepository;
import com.daoninhthai.aichatbot.repository.MessageRepository;
import com.daoninhthai.aichatbot.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.QuestionAnswerAdvisor;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor.CHAT_MEMORY_CONVERSATION_ID_KEY;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatClient chatClient;
    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final DocumentRepository documentRepository;
    private final VectorStore vectorStore;

    /**
     * Send a message and get a complete (non-streaming) response.
     */
    @Transactional
    public ChatMessageResponse sendMessage(Long conversationId, ChatMessageRequest request, User user) {
        Conversation conversation = getConversation(conversationId, user);

        // Save user message
        Message userMessage = saveMessage(conversation, Message.MessageRole.USER, request.getContent(), null);

        boolean enableRag = request.isUseRag() || documentRepository.existsByUserAndStatus(user, com.daoninhthai.aichatbot.entity.Document.ProcessingStatus.INDEXED);

        var promptSpec = chatClient.prompt()
                .user(request.getContent())
                .advisors(a -> a.param(CHAT_MEMORY_CONVERSATION_ID_KEY, conversationId.toString()));

        if (enableRag && vectorStore != null) {
            promptSpec.advisors(createRagAdvisor(user.getId()));
        }

        // Call AI
        String aiResponse = promptSpec.call().content();

        // Save assistant message
        Message assistantMessage = saveMessage(conversation, Message.MessageRole.ASSISTANT, aiResponse, "gpt-4o-mini");

        // Update conversation timestamp
        conversation.setUpdatedAt(java.time.LocalDateTime.now());
        conversationRepository.save(conversation);

        log.debug("Chat response generated for conversation {} (RAG: {})", conversationId, enableRag);
        return ChatMessageResponse.from(assistantMessage);
    }

    /**
     * Stream a response via Server-Sent Events — with optional RAG document retrieval.
     */
    public Flux<String> streamMessage(Long conversationId, String content, boolean useRag, User user) {
        Conversation conversation = getConversation(conversationId, user);

        // Save user message synchronously before streaming
        saveMessage(conversation, Message.MessageRole.USER, content, null);

        AtomicReference<StringBuilder> fullResponse = new AtomicReference<>(new StringBuilder());

        boolean enableRag = useRag || documentRepository.existsByUserAndStatus(user, com.daoninhthai.aichatbot.entity.Document.ProcessingStatus.INDEXED);

        var promptSpec = chatClient.prompt()
                .user(content)
                .advisors(a -> a.param(CHAT_MEMORY_CONVERSATION_ID_KEY, conversationId.toString()));

        if (enableRag && vectorStore != null) {
            promptSpec.advisors(createRagAdvisor(user.getId()));
        }

        return promptSpec
                .stream()
                .content()
                .doOnNext(chunk -> fullResponse.get().append(chunk))
                .doOnComplete(() -> {
                    // Persist the complete assistant response after streaming finishes
                    String completeResponse = fullResponse.get().toString();
                    if (!completeResponse.isEmpty()) {
                        saveMessage(conversation, Message.MessageRole.ASSISTANT, completeResponse, "gpt-4o-mini");
                        conversation.setUpdatedAt(java.time.LocalDateTime.now());
                        conversationRepository.save(conversation);
                        log.debug("Streamed response saved for conversation {} (RAG: {})", conversationId, enableRag);
                    }
                })
                .doOnError(error -> log.error("Streaming error for conversation {}: {}",
                        conversationId, error.getMessage()));
    }

    public Flux<String> streamMessage(Long conversationId, String content, User user) {
        return streamMessage(conversationId, content, false, user);
    }

    private QuestionAnswerAdvisor createRagAdvisor(Long userId) {
        SearchRequest searchRequest = SearchRequest.builder()
                .topK(4)
                .similarityThreshold(0.60)
                .filterExpression("userId == '" + userId + "'")
                .build();
        return new QuestionAnswerAdvisor(vectorStore, searchRequest);
    }

    /**
     * Get all messages in a conversation.
     */
    @Transactional(readOnly = true)
    public List<ChatMessageResponse> getMessages(Long conversationId, User user) {
        Conversation conversation = getConversation(conversationId, user);
        return messageRepository.findByConversationIdOrderByCreatedAtAsc(conversation.getId())
                .stream()
                .map(ChatMessageResponse::from)
                .toList();
    }

    private Conversation getConversation(Long conversationId, User user) {
        return conversationRepository.findByIdAndUser(conversationId, user)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation", conversationId));
    }

    private Message saveMessage(Conversation conversation, Message.MessageRole role, String content, String model) {
        Message message = Message.builder()
                .conversation(conversation)
                .role(role)
                .content(content)
                .model(model)
                .build();
        return messageRepository.save(message);
    }
}
