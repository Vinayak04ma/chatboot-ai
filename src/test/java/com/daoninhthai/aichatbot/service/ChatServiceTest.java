package com.daoninhthai.aichatbot.service;

import com.daoninhthai.aichatbot.dto.response.ChatMessageResponse;
import com.daoninhthai.aichatbot.entity.Conversation;
import com.daoninhthai.aichatbot.entity.Message;
import com.daoninhthai.aichatbot.entity.User;
import com.daoninhthai.aichatbot.exception.ResourceNotFoundException;
import com.daoninhthai.aichatbot.repository.ConversationRepository;
import com.daoninhthai.aichatbot.repository.DocumentRepository;
import com.daoninhthai.aichatbot.repository.MessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.vectorstore.VectorStore;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock
    private ChatClient chatClient;

    @Mock
    private ConversationRepository conversationRepository;

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private VectorStore vectorStore;

    @InjectMocks
    private ChatService chatService;

    private User sampleUser;
    private Conversation sampleConversation;

    @BeforeEach
    void setUp() {
        sampleUser = User.builder()
                .id(1L)
                .email("test@example.com")
                .displayName("Test User")
                .role(User.Role.USER)
                .build();

        sampleConversation = Conversation.builder()
                .id(100L)
                .user(sampleUser)
                .title("Test Conversation")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("Should retrieve all messages for a valid conversation")
    void getMessages_Success() {
        Message msg = Message.builder()
                .id(1L)
                .conversation(sampleConversation)
                .role(Message.MessageRole.USER)
                .content("Hello")
                .createdAt(LocalDateTime.now())
                .build();

        when(conversationRepository.findByIdAndUser(100L, sampleUser))
                .thenReturn(Optional.of(sampleConversation));
        when(messageRepository.findByConversationIdOrderByCreatedAtAsc(100L))
                .thenReturn(List.of(msg));

        List<ChatMessageResponse> messages = chatService.getMessages(100L, sampleUser);

        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).getContent()).isEqualTo("Hello");
        assertThat(messages.get(0).getRole()).isEqualTo("USER");
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when conversation not found or not owned by user")
    void getMessages_ConversationNotFound_ThrowsException() {
        when(conversationRepository.findByIdAndUser(999L, sampleUser))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> chatService.getMessages(999L, sampleUser))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Conversation");

        verify(messageRepository, never()).findByConversationIdOrderByCreatedAtAsc(any());
    }
}
