package com.example.email_service.service;

import com.example.email_service.dto.PaginationDTO;
import com.example.email_service.model.MailMessage;
import com.example.email_service.repository.MailMessageRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("MailboxService Unit Test")
class MailboxServiceTest {

    @Mock
    private MailMessageRepository mailRepo;

    @Mock
    private JavaMailSender mailSender;

    @InjectMocks
    private MailboxService mailboxService;

    @Test
    @DisplayName("COM-TC-001: sendGmail - tạo mail gửi đi đúng dữ liệu và lưu DB")
    void comTc001_sendGmail_shouldCreateAndPersistSentMail() {
        ReflectionTestUtils.setField(mailboxService, "fromEmail", "noreply@company.com");

        when(mailRepo.save(any(MailMessage.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MailMessage result = mailboxService.sendGmail("candidate@mail.com", "Offer", "Welcome aboard");

        ArgumentCaptor<SimpleMailMessage> outboundCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, times(1)).send(outboundCaptor.capture());

        SimpleMailMessage outbound = outboundCaptor.getValue();
        assertThat(outbound.getFrom()).isEqualTo("noreply@company.com");
        assertThat(outbound.getTo()).containsExactly("candidate@mail.com");
        assertThat(outbound.getSubject()).isEqualTo("Offer");
        assertThat(outbound.getText()).isEqualTo("Welcome aboard");

        ArgumentCaptor<MailMessage> entityCaptor = ArgumentCaptor.forClass(MailMessage.class);
        verify(mailRepo, times(1)).save(entityCaptor.capture());

        MailMessage saved = entityCaptor.getValue();
        assertThat(saved.isSent()).isTrue();
        assertThat(saved.getFromEmail()).isEqualTo("noreply@company.com");
        assertThat(saved.getToEmail()).isEqualTo("candidate@mail.com");
        assertThat(saved.getSubject()).isEqualTo("Offer");
        assertThat(saved.getContent()).isEqualTo("Welcome aboard");
        assertThat(saved.getGmailMessageId()).isNotBlank();

        assertThat(result.isSent()).isTrue();
        assertThat(result.getGmailMessageId()).isNotBlank();
    }

    @Test
    @DisplayName("COM-TC-002: getMailById - id không tồn tại ném RuntimeException")
    void comTc002_getMailById_whenNotFound_shouldThrowRuntimeException() {
        when(mailRepo.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> mailboxService.getMailById(999L))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Email không tồn tại");

        verify(mailRepo, times(1)).findById(999L);
    }

    @Test
    @DisplayName("COM-TC-003: getAllEmailsWithFilters - filter đúng folder, read và keyword")
    void comTc003_getAllEmailsWithFilters_shouldFilterInboxUnreadByKeyword() {
        MailMessage inboxUnreadMatch = buildMail(false, false, false, "Urgent update", "Body 1");
        MailMessage inboxRead = buildMail(false, true, false, "Urgent read", "Body 2");
        MailMessage sentUnread = buildMail(true, false, false, "Urgent sent", "Body 3");
        MailMessage inboxUnreadNoKeyword = buildMail(false, false, false, "Weekly report", "Body 4");

        Page<MailMessage> page = new PageImpl<>(List.of(inboxUnreadMatch, inboxRead, sentUnread, inboxUnreadNoKeyword));
        when(mailRepo.findByDeletedFalseOrderByCreatedAtDesc(any())).thenReturn(page);

        PaginationDTO result = mailboxService.getAllEmailsWithFilters(
                "inbox", false, "urgent", "createdAt", "desc", 1, 10);

        @SuppressWarnings("unchecked")
        List<MailMessage> filtered = (List<MailMessage>) result.getResult();

        assertThat(filtered).hasSize(1);
        assertThat(filtered.get(0).getSubject()).isEqualTo("Urgent update");
        assertThat(filtered.get(0).isSent()).isFalse();
        assertThat(filtered.get(0).isRead()).isFalse();
        verify(mailRepo, times(1)).findByDeletedFalseOrderByCreatedAtDesc(any());
    }

    @Test
    @DisplayName("COM-TC-004: getAllEmailsWithFilters - normalize page và limit đúng")
    void comTc004_getAllEmailsWithFilters_shouldNormalizePageAndLimit() {
        when(mailRepo.findByDeletedFalseOrderByCreatedAtDesc(any())).thenReturn(Page.empty());

        PaginationDTO result = mailboxService.getAllEmailsWithFilters(
                "all", null, null, "", "", 0, 500);

        assertThat(result.getMeta().getPage()).isEqualTo(1);
        assertThat(result.getMeta().getPageSize()).isEqualTo(100);
        verify(mailRepo, times(1)).findByDeletedFalseOrderByCreatedAtDesc(any());
    }

    @Test
    @DisplayName("COM-TC-005: markRead - cập nhật trạng thái read thành true")
    void comTc005_markRead_shouldUpdateReadToTrue() {
        MailMessage mail = buildMail(false, false, false, "Subject", "Content");
        when(mailRepo.findById(5L)).thenReturn(Optional.of(mail));

        mailboxService.markRead(5L);

        ArgumentCaptor<MailMessage> captor = ArgumentCaptor.forClass(MailMessage.class);
        verify(mailRepo, times(1)).save(captor.capture());
        assertThat(captor.getValue().isRead()).isTrue();
    }

    @Test
    @DisplayName("COM-EXT-001: getAllEmailsWithFilters - list rỗng trả về result rỗng")
    void ext001_getAllEmailsWithFilters_whenEmptyList_shouldReturnEmptyResult() {
        when(mailRepo.findByDeletedFalseOrderByCreatedAtDesc(any())).thenReturn(Page.empty());

        PaginationDTO result = mailboxService.getAllEmailsWithFilters(
                "inbox", null, "keyword", "createdAt", "desc", 1, 10);

        @SuppressWarnings("unchecked")
        List<MailMessage> filtered = (List<MailMessage>) result.getResult();
        assertThat(filtered).isEmpty();
        assertThat(result.getMeta().getTotal()).isZero();
    }

    @Test
    @DisplayName("COM-EXT-002: getAllEmailsWithFilters - keyword null/empty không lọc theo keyword")
    void ext002_getAllEmailsWithFilters_whenKeywordNullOrEmpty_shouldNotFilterByKeyword() {
        MailMessage inboxUnreadA = buildMail(false, false, false, "A", "X");
        MailMessage inboxUnreadB = buildMail(false, false, false, "B", "Y");
        when(mailRepo.findByDeletedFalseOrderByCreatedAtDesc(any()))
                .thenReturn(new PageImpl<>(List.of(inboxUnreadA, inboxUnreadB)));

        PaginationDTO nullKeyword = mailboxService.getAllEmailsWithFilters(
                "inbox", false, null, "createdAt", "desc", 1, 10);
        PaginationDTO emptyKeyword = mailboxService.getAllEmailsWithFilters(
                "inbox", false, "", "createdAt", "desc", 1, 10);

        @SuppressWarnings("unchecked")
        List<MailMessage> resultNull = (List<MailMessage>) nullKeyword.getResult();
        @SuppressWarnings("unchecked")
        List<MailMessage> resultEmpty = (List<MailMessage>) emptyKeyword.getResult();

        assertThat(resultNull).hasSize(2);
        assertThat(resultEmpty).hasSize(2);
    }

    @Test
    @DisplayName("COM-EXT-003: getAllEmailsWithFilters - folder sent và read=true lọc đúng")
    void ext003_getAllEmailsWithFilters_shouldFilterSentAndRead() {
        MailMessage sentRead = buildMail(true, true, false, "Done", "Email sent");
        MailMessage sentUnread = buildMail(true, false, false, "Pending", "Email sent");
        MailMessage inboxRead = buildMail(false, true, false, "Inbox", "Email inbox");
        when(mailRepo.findByDeletedFalseOrderByCreatedAtDesc(any()))
                .thenReturn(new PageImpl<>(List.of(sentRead, sentUnread, inboxRead)));

        PaginationDTO result = mailboxService.getAllEmailsWithFilters(
                "sent", true, null, "createdAt", "desc", 1, 10);

        @SuppressWarnings("unchecked")
        List<MailMessage> filtered = (List<MailMessage>) result.getResult();
        assertThat(filtered).hasSize(1);
        assertThat(filtered.get(0).getSubject()).isEqualTo("Done");
        assertThat(filtered.get(0).isSent()).isTrue();
        assertThat(filtered.get(0).isRead()).isTrue();
    }

    @Test
    @DisplayName("COM-EXT-004: markRead - id không tồn tại thì không save")
    void ext004_markRead_whenIdNotFound_shouldNotSave() {
        when(mailRepo.findById(999L)).thenReturn(Optional.empty());

        mailboxService.markRead(999L);

        verify(mailRepo, times(1)).findById(999L);
        verify(mailRepo, never()).save(any(MailMessage.class));
    }

    @Test
    @DisplayName("COM-EXT-005: sendGmail - lỗi gửi mail thì ném exception và không lưu DB")
    void ext005_sendGmail_whenMailSenderFails_shouldThrowAndNotSave() {
        ReflectionTestUtils.setField(mailboxService, "fromEmail", "noreply@company.com");
        RuntimeException sendError = new RuntimeException("SMTP error");

        org.mockito.Mockito.doThrow(sendError).when(mailSender).send(any(SimpleMailMessage.class));

        assertThatThrownBy(() -> mailboxService.sendGmail("to@mail.com", "S", "C"))
                .isSameAs(sendError);

        verify(mailSender, times(1)).send(any(SimpleMailMessage.class));
        verify(mailRepo, never()).save(any(MailMessage.class));
    }

    private MailMessage buildMail(boolean sent, boolean read, boolean deleted, String subject, String content) {
        MailMessage mail = new MailMessage();
        mail.setSent(sent);
        mail.setRead(read);
        mail.setDeleted(deleted);
        mail.setSubject(subject);
        mail.setContent(content);
        return mail;
    }
}

