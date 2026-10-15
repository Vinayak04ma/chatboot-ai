package com.daoninhthai.aichatbot.service;

import com.daoninhthai.aichatbot.dto.response.DocumentResponse;
import com.daoninhthai.aichatbot.entity.Document;
import com.daoninhthai.aichatbot.entity.User;
import com.daoninhthai.aichatbot.exception.BadRequestException;
import com.daoninhthai.aichatbot.exception.ResourceNotFoundException;
import com.daoninhthai.aichatbot.repository.DocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.mock.web.MockMultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private VectorStore vectorStore;

    @InjectMocks
    private DocumentService documentService;

    private User sampleUser;
    private Document sampleDoc;

    @BeforeEach
    void setUp() {
        sampleUser = User.builder()
                .id(1L)
                .email("test@example.com")
                .displayName("Test User")
                .role(User.Role.USER)
                .build();

        sampleDoc = Document.builder()
                .id(10L)
                .user(sampleUser)
                .fileName("test.pdf")
                .contentType("application/pdf")
                .fileSize(1024L)
                .chunkCount(5)
                .status(Document.ProcessingStatus.INDEXED)
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("Should reject empty file upload")
    void uploadDocument_EmptyFile_ThrowsBadRequest() {
        MockMultipartFile emptyFile = new MockMultipartFile(
                "file", "empty.pdf", "application/pdf", new byte[0]);

        assertThatThrownBy(() -> documentService.uploadDocument(emptyFile, sampleUser))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("File is empty");

        verify(documentRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should reject unsupported file type")
    void uploadDocument_UnsupportedType_ThrowsBadRequest() {
        MockMultipartFile exeFile = new MockMultipartFile(
                "file", "script.exe", "application/x-msdownload", "binary".getBytes());

        assertThatThrownBy(() -> documentService.uploadDocument(exeFile, sampleUser))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("File type not supported");

        verify(documentRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should fetch user documents successfully")
    void getUserDocuments_Success() {
        when(documentRepository.findByUserOrderByCreatedAtDesc(sampleUser))
                .thenReturn(List.of(sampleDoc));

        List<DocumentResponse> result = documentService.getUserDocuments(sampleUser);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getFileName()).isEqualTo("test.pdf");
        assertThat(result.get(0).getStatus()).isEqualTo("INDEXED");
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when document does not belong to user")
    void getDocument_NotFound_ThrowsException() {
        when(documentRepository.findByIdAndUser(99L, sampleUser))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> documentService.getDocument(99L, sampleUser))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Should delete document and remove its vectors from vector store")
    void deleteDocument_Success() {
        when(documentRepository.findByIdAndUser(10L, sampleUser))
                .thenReturn(Optional.of(sampleDoc));

        documentService.deleteDocument(10L, sampleUser);

        verify(vectorStore, times(1)).delete(List.of("documentId == '10'"));
        verify(documentRepository, times(1)).delete(sampleDoc);
    }
}
