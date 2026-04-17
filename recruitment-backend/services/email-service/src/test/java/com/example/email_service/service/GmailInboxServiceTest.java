package com.example.email_service.service;

import com.example.email_service.model.MailMessage;
import com.example.email_service.repository.MailMessageRepository;
import jakarta.mail.Address;
import jakarta.mail.BodyPart;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMultipart;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Date;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("GmailInboxService Unit Test")
class GmailInboxServiceTest {

    @Mock
    private MailMessageRepository mailRepo;

    @InjectMocks
    private GmailInboxService gmailInboxService;

    @Test
    @DisplayName("COM-TC-006: fetchAndSaveEmails - bỏ qua email đã tồn tại")
    void comTc006_fetchAndSaveEmails_shouldSkipDuplicateMessage() throws Exception {
        ReflectionTestUtils.setField(gmailInboxService, "gmailUsername", "user@gmail.com");
        ReflectionTestUtils.setField(gmailInboxService, "gmailPassword", "app-password");

        Message duplicate = mock(Message.class);
        when(duplicate.getHeader("Message-ID")).thenReturn(new String[]{"<msg-duplicate@gmail.com>"});
        when(mailRepo.existsByGmailMessageId("<msg-duplicate@gmail.com>")).thenReturn(true);

        withMailInfrastructure(new Message[]{duplicate}, () -> gmailInboxService.fetchAndSaveEmails());

        verify(mailRepo, times(1)).existsByGmailMessageId("<msg-duplicate@gmail.com>");
        verify(mailRepo, never()).save(any(MailMessage.class));
    }

    @Test
    @DisplayName("COM-TC-007: fetchAndSaveEmails - continue khi gặp lỗi, vẫn lưu message hợp lệ")
    void comTc007_fetchAndSaveEmails_shouldContinueWhenOneMessageFails() throws Exception {
        ReflectionTestUtils.setField(gmailInboxService, "gmailUsername", "user@gmail.com");
        ReflectionTestUtils.setField(gmailInboxService, "gmailPassword", "app-password");

        Message invalidMessage = mock(Message.class);
        when(invalidMessage.getHeader("Message-ID")).thenThrow(new RuntimeException("broken message"));

        Message validMessage = mock(Message.class);
        when(validMessage.getHeader("Message-ID")).thenReturn(new String[]{"<msg-valid@gmail.com>"});
        when(mailRepo.existsByGmailMessageId("<msg-valid@gmail.com>")).thenReturn(false);
        when(validMessage.getFrom()).thenReturn(new Address[]{new InternetAddress("from@mail.com")});
        when(validMessage.getRecipients(Message.RecipientType.TO))
                .thenReturn(new Address[]{new InternetAddress("to@mail.com")});
        when(validMessage.getSubject()).thenReturn("Hello");
        when(validMessage.getContent()).thenReturn("Plain content");
        when(validMessage.getSentDate()).thenReturn(new Date());

        withMailInfrastructure(new Message[]{invalidMessage, validMessage}, () -> gmailInboxService.fetchAndSaveEmails());

        verify(mailRepo, times(1)).save(any(MailMessage.class));
    }

    @Test
    @DisplayName("COM-EXT-006: fetchAndSaveEmails - try/catch ngoài cùng bắt lỗi connect")
    void ext006_fetchAndSaveEmails_whenConnectFails_shouldNotThrow() throws Exception {
        ReflectionTestUtils.setField(gmailInboxService, "gmailUsername", "user@gmail.com");
        ReflectionTestUtils.setField(gmailInboxService, "gmailPassword", "app-password");

        try (MockedStatic<Session> sessionStatic = mockStatic(Session.class)) {
            Session session = mock(Session.class);
            Store store = mock(Store.class);

            sessionStatic.when(() -> Session.getInstance(any(Properties.class))).thenReturn(session);
            when(session.getStore("imaps")).thenReturn(store);
            org.mockito.Mockito.doThrow(new RuntimeException("auth fail"))
                    .when(store).connect("imap.gmail.com", "user@gmail.com", "app-password");

            assertThatCode(() -> gmailInboxService.fetchAndSaveEmails()).doesNotThrowAnyException();
            verify(mailRepo, never()).save(any(MailMessage.class));
        }
    }

    @Test
    @DisplayName("COM-EXT-007: fetchAndSaveEmails - mapping MailMessage từ Gmail đúng dữ liệu")
    void ext007_fetchAndSaveEmails_shouldMapAndPersistMailMessageFields() throws Exception {
        ReflectionTestUtils.setField(gmailInboxService, "gmailUsername", "user@gmail.com");
        ReflectionTestUtils.setField(gmailInboxService, "gmailPassword", "app-password");

        Date sentDate = new Date();
        Message validMessage = mock(Message.class);
        when(validMessage.getHeader("Message-ID")).thenReturn(new String[]{"<msg-map@gmail.com>"});
        when(mailRepo.existsByGmailMessageId("<msg-map@gmail.com>")).thenReturn(false);
        when(validMessage.getFrom()).thenReturn(new Address[]{new InternetAddress("sender@mail.com")});
        when(validMessage.getRecipients(Message.RecipientType.TO))
                .thenReturn(new Address[]{new InternetAddress("receiver@mail.com")});
        when(validMessage.getSubject()).thenReturn("Mapped subject");
        when(validMessage.getContent()).thenReturn("Mapped body");
        when(validMessage.getSentDate()).thenReturn(sentDate);

        withMailInfrastructure(new Message[]{validMessage}, () -> gmailInboxService.fetchAndSaveEmails());

        ArgumentCaptor<MailMessage> captor = ArgumentCaptor.forClass(MailMessage.class);
        verify(mailRepo, times(1)).save(captor.capture());

        MailMessage saved = captor.getValue();
        assertThat(saved.getFromEmail()).isEqualTo("sender@mail.com");
        assertThat(saved.getToEmail()).isEqualTo("receiver@mail.com");
        assertThat(saved.getSubject()).isEqualTo("Mapped subject");
        assertThat(saved.getContent()).isEqualTo("Mapped body");
        assertThat(saved.getGmailMessageId()).isEqualTo("<msg-map@gmail.com>");
        assertThat(saved.isSent()).isFalse();
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("COM-EXT-008: fetchAndSaveEmails - Message-ID null thì tạo id mới và vẫn lưu")
    void ext008_fetchAndSaveEmails_whenMessageIdMissing_shouldGenerateAndSave() throws Exception {
        ReflectionTestUtils.setField(gmailInboxService, "gmailUsername", "user@gmail.com");
        ReflectionTestUtils.setField(gmailInboxService, "gmailPassword", "app-password");

        Message messageWithoutId = mock(Message.class);
        when(messageWithoutId.getHeader("Message-ID")).thenReturn(null);
        when(messageWithoutId.getFrom()).thenReturn(new Address[]{new InternetAddress("sender@mail.com")});
        when(messageWithoutId.getRecipients(Message.RecipientType.TO))
                .thenReturn(new Address[]{new InternetAddress("receiver@mail.com")});
        when(messageWithoutId.getSubject()).thenReturn(null);
        when(messageWithoutId.getContent()).thenReturn("Body without id");
        when(messageWithoutId.getSentDate()).thenReturn(null);

        withMailInfrastructure(new Message[]{messageWithoutId}, () -> gmailInboxService.fetchAndSaveEmails());

        ArgumentCaptor<MailMessage> captor = ArgumentCaptor.forClass(MailMessage.class);
        verify(mailRepo, times(1)).save(captor.capture());

        MailMessage saved = captor.getValue();
        assertThat(saved.getGmailMessageId()).isNotBlank();
        assertThat(saved.getSubject()).isEqualTo("(No Subject)");
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("COM-EXT-009: fetchAndSaveEmails - extractContent đọc multipart text/plain + text/html")
    void ext009_fetchAndSaveEmails_shouldExtractMultipartContent() throws Exception {
        ReflectionTestUtils.setField(gmailInboxService, "gmailUsername", "user@gmail.com");
        ReflectionTestUtils.setField(gmailInboxService, "gmailPassword", "app-password");

        Message message = mock(Message.class);
        when(message.getHeader("Message-ID")).thenReturn(new String[]{"<msg-multi@gmail.com>"});
        when(mailRepo.existsByGmailMessageId("<msg-multi@gmail.com>")).thenReturn(false);
        when(message.getFrom()).thenReturn(new Address[]{new InternetAddress("sender@mail.com")});
        when(message.getRecipients(Message.RecipientType.TO))
                .thenReturn(new Address[]{new InternetAddress("receiver@mail.com")});
        when(message.getSubject()).thenReturn("Multipart");
        when(message.getSentDate()).thenReturn(new Date());

        MimeMultipart multipart = mock(MimeMultipart.class);
        BodyPart textPart = mock(BodyPart.class);
        BodyPart htmlPart = mock(BodyPart.class);

        when(multipart.getCount()).thenReturn(2);
        when(multipart.getBodyPart(0)).thenReturn(textPart);
        when(multipart.getBodyPart(1)).thenReturn(htmlPart);

        when(textPart.isMimeType("text/plain")).thenReturn(true);
        when(textPart.getContent()).thenReturn("Plain-");
        when(htmlPart.isMimeType("text/plain")).thenReturn(false);
        when(htmlPart.isMimeType("text/html")).thenReturn(true);
        when(htmlPart.getContent()).thenReturn("Html");

        when(message.getContent()).thenReturn(multipart);

        withMailInfrastructure(new Message[]{message}, () -> gmailInboxService.fetchAndSaveEmails());

        ArgumentCaptor<MailMessage> captor = ArgumentCaptor.forClass(MailMessage.class);
        verify(mailRepo, times(1)).save(captor.capture());
        assertThat(captor.getValue().getContent()).isEqualTo("Plain-Html");
    }

    @Test
    @DisplayName("COM-EXT-010: fetchAndSaveEmails - message lỗi toàn bộ thì không save")
    void ext010_fetchAndSaveEmails_whenAllMessagesFail_shouldNotSave() throws Exception {
        ReflectionTestUtils.setField(gmailInboxService, "gmailUsername", "user@gmail.com");
        ReflectionTestUtils.setField(gmailInboxService, "gmailPassword", "app-password");

        Message broken = mock(Message.class);
        when(broken.getHeader("Message-ID")).thenThrow(new RuntimeException("cannot read message"));

        withMailInfrastructure(new Message[]{broken}, () -> gmailInboxService.fetchAndSaveEmails());

        verify(mailRepo, never()).save(any(MailMessage.class));
    }

    private void withMailInfrastructure(Message[] messages, ThrowingRunnable runnable) throws Exception {
        try (MockedStatic<Session> sessionStatic = mockStatic(Session.class)) {
            Session session = mock(Session.class);
            Store store = mock(Store.class);
            Folder inbox = mock(Folder.class);

            sessionStatic.when(() -> Session.getInstance(any(Properties.class))).thenReturn(session);
            when(session.getStore("imaps")).thenReturn(store);
            when(store.getFolder(eq("INBOX"))).thenReturn(inbox);
            when(inbox.getMessages()).thenReturn(messages);

            runnable.run();

            verify(store, times(1)).connect(any(String.class), any(String.class), any(String.class));
            verify(inbox, times(1)).open(Folder.READ_ONLY);
            verify(inbox, times(1)).close(false);
            verify(store, times(1)).close();
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}

