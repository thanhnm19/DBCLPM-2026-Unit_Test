package com.example.job_service;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestWatcher;

import java.util.Optional;

/**
 * JUnit 5 TestWatcher Extension — In kết quả PASS/FAIL cho từng test case.
 *
 * Cách dùng: Thêm @ExtendWith(TestResultLogger.class) vào class test.
 *
 * Output mẫu:
 *   [PASS] tc01_create_validInput_persistsAllFieldsWithDraftStatus
 *   [FAIL] tc04_submit_fromPending_throwsIllegalStateException_dbUnchanged
 *          → BUG: Status trong DB bị thay đổi dù submit thất bại
 */
public class TestResultLogger implements TestWatcher {

    private static final int NAME_WIDTH = 75;

    @Override
    public void testSuccessful(ExtensionContext context) {
        String name = context.getDisplayName();
        System.out.printf("%n%-" + NAME_WIDTH + "s  \u001B[32m[PASS]\u001B[0m%n", truncate(name));
    }

    @Override
    public void testFailed(ExtensionContext context, Throwable cause) {
        String name = context.getDisplayName();
        System.out.printf("%n%-" + NAME_WIDTH + "s  \u001B[31m[FAIL]\u001B[0m%n", truncate(name));
        if (cause != null && cause.getMessage() != null) {
            String msg = cause.getMessage().lines().findFirst().orElse("").trim();
            if (!msg.isEmpty()) {
                System.out.printf("  \u001B[33m→ %s\u001B[0m%n", truncate(msg));
            }
        }
    }

    @Override
    public void testAborted(ExtensionContext context, Throwable cause) {
        String name = context.getDisplayName();
        System.out.printf("%n%-" + NAME_WIDTH + "s  \u001B[33m[ABRT]\u001B[0m%n", truncate(name));
    }

    @Override
    public void testDisabled(ExtensionContext context, Optional<String> reason) {
        String name = context.getDisplayName();
        System.out.printf("%n%-" + NAME_WIDTH + "s  \u001B[90m[SKIP]\u001B[0m%n", truncate(name));
    }

    private String truncate(String s) {
        if (s == null) return "";
        return s.length() > NAME_WIDTH ? s.substring(0, NAME_WIDTH - 3) + "..." : s;
    }
}
