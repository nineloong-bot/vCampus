package edu.seu.vcampus.client.student;

import edu.seu.vcampus.client.core.ui.theme.UiBorders;
import edu.seu.vcampus.client.core.ui.theme.UiColors;
import edu.seu.vcampus.client.core.ui.theme.UiDimensions;
import edu.seu.vcampus.client.core.ui.theme.UiSpacing;
import edu.seu.vcampus.client.student.service.StudentClientService;
import edu.seu.vcampus.client.student.service.StudentRequestClient;
import edu.seu.vcampus.client.student.ui.UpdateContactDialog;
import edu.seu.vcampus.common.protocol.ResponseBody;
import edu.seu.vcampus.common.student.StudentStatus;
import edu.seu.vcampus.common.student.StudentType;
import edu.seu.vcampus.common.student.StudentView;
import edu.seu.vcampus.common.student.UpdateStudentContactCommand;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import javax.swing.plaf.basic.BasicButtonUI;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.Serializable;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class UpdateContactDialogTest {
    @AfterEach
    void disposeWindows() throws Exception {
        SwingUtilities.invokeAndWait(() -> Arrays.stream(Window.getWindows()).forEach(Window::dispose));
    }

    @Test
    void saveUsesCurrentStudentAndVersionThenPublishesReturnedProfile() throws Exception {
        var client = new RecordingStudentClient();
        var service = new StudentClientService(client, Duration.ofSeconds(3));
        var saved = new AtomicReference<StudentView>();
        var savedCount = new AtomicInteger();
        var savedOnEdt = new AtomicBoolean();
        var savedSignal = new CountDownLatch(1);
        UpdateContactDialog dialog = onEdt(() -> displayed(new UpdateContactDialog(
                null, service, profile(7, "old@seu.edu.cn", "13000000000"), value -> {
                    saved.set(value);
                    savedCount.incrementAndGet();
                    savedOnEdt.set(SwingUtilities.isEventDispatchThread());
                    savedSignal.countDown();
                })));

        onEdt(() -> {
            field(dialog, "student.contact.email").setText("new@seu.edu.cn");
            field(dialog, "student.contact.phone").setText("13800000000");
            button(dialog, "student.contact.submit").doClick();
        });
        var call = client.await("STUDENT_UPDATE_CONTACT");
        awaitServiceDependent(call.response());
        client.complete(call, ResponseBody.success(profile(8, "new@seu.edu.cn", "13800000000")));
        assertThat(savedSignal.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(client.complete(call, ResponseBody.success(profile(9, "late@seu.edu.cn", "13900000000")))).isFalse();

        assertThat(call.body()).isEqualTo(new UpdateStudentContactCommand(
                "student-1", "new@seu.edu.cn", "13800000000", 7));
        assertThat(saved.get().rowVersion()).isEqualTo(8);
        assertThat(savedCount).hasValue(1);
        assertThat(savedOnEdt).isTrue();
        assertThat(dialog.isDisplayable()).isFalse();
    }

    @Test
    void blankContactValuesSerializeAsNull() throws Exception {
        var client = new RecordingStudentClient();
        UpdateContactDialog dialog = dialog(client, profile(4, "old@seu.edu.cn", "130"));
        onEdt(() -> {
            field(dialog, "student.contact.email").setText("   ");
            field(dialog, "student.contact.phone").setText("\t");
            button(dialog, "student.contact.submit").doClick();
        });
        var call = client.await("STUDENT_UPDATE_CONTACT");
        assertThat(call.body()).isEqualTo(new UpdateStudentContactCommand("student-1", null, null, 4));
    }

    @Test
    void malformedEmailIsRejectedBeforeSending() throws Exception {
        var client = new RecordingStudentClient();
        UpdateContactDialog dialog = dialog(client, profile(4, "old@seu.edu.cn", "130"));
        onEdt(() -> {
            field(dialog, "student.contact.email").setText("not-an-email");
            button(dialog, "student.contact.submit").doClick();
        });
        flushEdt();

        assertThat(client.poll()).isNull();
        assertThat(label(dialog, "student.contact.error").getText()).contains("邮箱", "格式");
        assertThat(field(dialog, "student.contact.email").getText()).isEqualTo("not-an-email");
    }

    @Test
    void dialogUsesAccessibleControlsAndTheRequiredFixedSize() throws Exception {
        var client = new RecordingStudentClient();
        UpdateContactDialog dialog = dialog(client, profile(4, "old@seu.edu.cn", "130"));

        assertThat(dialog.getSize()).isEqualTo(new Dimension(560, 360));
        assertThat(dialog.isResizable()).isFalse();
        assertThat(dialog.getRootPane().getDefaultButton()).isSameAs(button(dialog, "student.contact.submit"));
        for (String name : new String[]{"student.contact.email", "student.contact.phone",
                "student.contact.cancel", "student.contact.refresh", "student.contact.submit"}) {
            assertThat(component(dialog, name, JComponent.class)
                    .getAccessibleContext().getAccessibleName()).isNotBlank();
        }
    }

    @Test
    void primarySaveUsesTokenColorsWithPlatformIndependentPainting() throws Exception {
        UpdateContactDialog dialog = dialog(new RecordingStudentClient(), profile(4, "old@seu.edu.cn", "130"));
        JButton submit = button(dialog, "student.contact.submit");

        assertThat(submit.getUI()).isInstanceOf(BasicButtonUI.class);
        assertThat(submit.isOpaque()).isTrue();
        assertThat(submit.isContentAreaFilled()).isTrue();
        assertThat(submit.getBackground()).isEqualTo(UiColors.ACCENT);
        assertThat(submit.getForeground()).isEqualTo(UiColors.TEXT_ON_PRIMARY);
        assertThat(submit.getBorder()).isInstanceOf(CompoundBorder.class);
        CompoundBorder border = (CompoundBorder) submit.getBorder();
        assertThat(border.getOutsideBorder()).isSameAs(UiBorders.LINE);
        assertThat(border.getInsideBorder()).isInstanceOf(EmptyBorder.class);
        assertThat(border.getBorderInsets(submit)).isEqualTo(new Insets(
                UiSpacing.SPACE_2 + 1, UiSpacing.SPACE_4 + 1,
                UiSpacing.SPACE_2 + 1, UiSpacing.SPACE_4 + 1));
        assertThat(submit.getPreferredSize().height).isGreaterThanOrEqualTo(UiDimensions.CONTROL_HEIGHT);
        assertThat(submit.isFocusPainted()).isTrue();
    }

    @Test
    void primarySaveFocusSwapsToTheSharedFocusBorderWithoutChangingInsets() throws Exception {
        UpdateContactDialog dialog = dialog(new RecordingStudentClient(), profile(4, "old@seu.edu.cn", "130"));
        JButton submit = button(dialog, "student.contact.submit");
        Insets defaultInsets = submit.getBorder().getBorderInsets(submit);

        onEdt(() -> {
            FocusEvent gained = new FocusEvent(submit, FocusEvent.FOCUS_GAINED);
            for (FocusListener listener : submit.getFocusListeners()) listener.focusGained(gained);
        });
        CompoundBorder focused = (CompoundBorder) submit.getBorder();
        assertThat(focused.getOutsideBorder()).isSameAs(UiBorders.FOCUS);
        assertThat(focused.getBorderInsets(submit)).isEqualTo(defaultInsets);

        onEdt(() -> {
            FocusEvent lost = new FocusEvent(submit, FocusEvent.FOCUS_LOST);
            for (FocusListener listener : submit.getFocusListeners()) listener.focusLost(lost);
        });
        assertThat(((CompoundBorder) submit.getBorder()).getOutsideBorder()).isSameAs(UiBorders.LINE);
    }

    @Test
    void dialogTraversalUsesOnlyEligibleControlsInLiteralUiStates() throws Exception {
        UpdateContactDialog dialog = dialog(new RecordingStudentClient(), profile(4, "old@seu.edu.cn", "130"));
        FocusTraversalPolicy focus = dialog.getFocusTraversalPolicy();
        JTextField email = field(dialog, "student.contact.email");
        JTextField phone = field(dialog, "student.contact.phone");
        JButton cancel = button(dialog, "student.contact.cancel");
        JButton refresh = button(dialog, "student.contact.refresh");
        JButton submit = button(dialog, "student.contact.submit");

        assertThat(focus.getDefaultComponent(dialog)).isSameAs(email);
        assertThat(focus.getComponentAfter(dialog, email)).isSameAs(phone);
        assertThat(focus.getComponentAfter(dialog, phone)).isSameAs(cancel);
        assertThat(focus.getComponentAfter(dialog, cancel)).isSameAs(submit);
        assertThat(focus.getComponentAfter(dialog, submit)).isSameAs(email);
        onEdt(() -> refresh.setVisible(true));
        onEdt(() -> submit.setEnabled(false));
        assertThat(focus.getComponentAfter(dialog, cancel)).isSameAs(refresh);
        assertThat(focus.getComponentAfter(dialog, refresh)).isSameAs(email);
        onEdt(() -> {
            email.setEnabled(false);
            phone.setEnabled(false);
            cancel.setEnabled(false);
            refresh.setEnabled(false);
        });
        assertThat(focus.getDefaultComponent(dialog)).isNull();
        assertThat(focus.getComponentAfter(dialog, email)).isNull();
    }

    @Test
    void openingDialogStartsKeyboardTraversalAtEmail() throws Exception {
        var client = new RecordingStudentClient();
        var emailFocused = new CountDownLatch(1);
        var closed = new CountDownLatch(1);
        var dialog = new AtomicReference<UpdateContactDialog>();
        SwingUtilities.invokeLater(() -> {
            UpdateContactDialog shown = new UpdateContactDialog(null,
                    new StudentClientService(client, Duration.ofSeconds(3)), profile(4, "old@seu.edu.cn", "130"),
                    ignored -> { });
            shown.addWindowListener(new WindowAdapter() {
                @Override public void windowClosed(WindowEvent event) { closed.countDown(); }
            });
            field(shown, "student.contact.email").addFocusListener(new FocusAdapter() {
                @Override public void focusGained(FocusEvent event) { emailFocused.countDown(); }
            });
            dialog.set(shown);
            shown.setVisible(true);
        });
        assertThat(emailFocused.await(2, TimeUnit.SECONDS)).isTrue();
        onEdt(() -> assertThat(field(dialog.get(), "student.contact.email").isFocusOwner()).isTrue());
        onEdt(() -> dialog.get().dispose());
        assertThat(closed.await(2, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void submissionDisablesInputsAndUsesSavingLabel() throws Exception {
        var client = new RecordingStudentClient();
        UpdateContactDialog dialog = dialog(client, profile(4, "old@seu.edu.cn", "130"));
        onEdt(() -> button(dialog, "student.contact.submit").doClick());
        client.await("STUDENT_UPDATE_CONTACT");

        assertThat(field(dialog, "student.contact.email").isEnabled()).isFalse();
        assertThat(field(dialog, "student.contact.phone").isEnabled()).isFalse();
        assertThat(button(dialog, "student.contact.submit").isEnabled()).isFalse();
        assertThat(button(dialog, "student.contact.submit").getText()).isEqualTo("正在保存");
    }

    @Test
    void exceptionalSaveKeepsTypedValuesAndShowsGenericSafeErrorOnEdt() throws Exception {
        var client = new RecordingStudentClient();
        UpdateContactDialog dialog = dialog(client, profile(4, "old@seu.edu.cn", "130"));
        var updatedOnEdt = new AtomicBoolean();
        onEdt(() -> {
            field(dialog, "student.contact.email").setText("typed@seu.edu.cn");
            field(dialog, "student.contact.phone").setText("13900000000");
            button(dialog, "student.contact.submit").doClick();
        });
        var call = client.await("STUDENT_UPDATE_CONTACT");
        var errorSignal = onEdt(() -> signal(label(dialog, "student.contact.error"), "text", updatedOnEdt));
        awaitServiceDependent(call.response());
        client.fail(call, new IllegalStateException("internal.Secret"));
        await(errorSignal);

        assertThat(field(dialog, "student.contact.email").getText()).isEqualTo("typed@seu.edu.cn");
        assertThat(field(dialog, "student.contact.phone").getText()).isEqualTo("13900000000");
        assertThat(label(dialog, "student.contact.error").getText())
                .isEqualTo("保存失败，请稍后重试").doesNotContain("Secret", "internal");
        assertThat(updatedOnEdt).isTrue();
    }

    @Test
    void ordinaryFailedResponseKeepsTypedValuesAndHidesInternalCode() throws Exception {
        var client = new RecordingStudentClient();
        var published = new AtomicInteger();
        UpdateContactDialog dialog = onEdt(() -> displayed(new UpdateContactDialog(null,
                new StudentClientService(client, Duration.ofSeconds(3)), profile(4, "old@seu.edu.cn", "130"),
                ignored -> published.incrementAndGet())));
        onEdt(() -> {
            field(dialog, "student.contact.email").setText("typed@seu.edu.cn");
            field(dialog, "student.contact.phone").setText("13900000000");
            button(dialog, "student.contact.submit").doClick();
        });
        var call = client.await("STUDENT_UPDATE_CONTACT");
        var errorSignal = onEdt(() -> signal(label(dialog, "student.contact.error"), "text"));
        awaitServiceDependent(call.response());
        client.complete(call, ResponseBody.failure("INTERNAL_DENIED", "没有权限修改联系方式", null));
        await(errorSignal);

        assertThat(field(dialog, "student.contact.email").getText()).isEqualTo("typed@seu.edu.cn");
        assertThat(field(dialog, "student.contact.phone").getText()).isEqualTo("13900000000");
        assertThat(label(dialog, "student.contact.error").getText())
                .contains("没有权限修改联系方式").doesNotContain("INTERNAL_DENIED");
        assertThat(button(dialog, "student.contact.submit").isEnabled()).isTrue();
        assertThat(published).hasValue(0);
    }

    @Test
    void conflictRequiresExplicitRefreshAndRetainsTypedValuesUntilNewVersionIsSaved() throws Exception {
        var client = new RecordingStudentClient();
        UpdateContactDialog dialog = dialog(client, profile(4, "old@seu.edu.cn", "130"));
        onEdt(() -> {
            field(dialog, "student.contact.email").setText("typed@seu.edu.cn");
            field(dialog, "student.contact.phone").setText("13900000000");
            button(dialog, "student.contact.submit").doClick();
        });
        var save = client.await("STUDENT_UPDATE_CONTACT");
        var conflictSignal = onEdt(() -> signal(label(dialog, "student.contact.error"), "text"));
        awaitServiceDependent(save.response());
        client.complete(save, ResponseBody.failure("COMMON_CONCURRENT_MODIFICATION", "数据已被修改，请刷新", null));
        await(conflictSignal);

        assertThat(field(dialog, "student.contact.email").getText()).isEqualTo("typed@seu.edu.cn");
        assertThat(field(dialog, "student.contact.phone").getText()).isEqualTo("13900000000");
        assertThat(button(dialog, "student.contact.submit").isEnabled()).isFalse();
        assertThat(button(dialog, "student.contact.refresh").isVisible()).isTrue();
        assertThat(label(dialog, "student.contact.error").getText()).contains("刷新数据");

        onEdt(() -> button(dialog, "student.contact.refresh").doClick());
        var refresh = client.await("STUDENT_GET_CURRENT");
        var refreshedSignal = onEdt(() -> signal(label(dialog, "student.contact.error"), "text"));
        awaitServiceDependent(refresh.response());
        client.complete(refresh, ResponseBody.success(profile(9, "server@seu.edu.cn", "13700000000")));
        await(refreshedSignal);

        assertThat(field(dialog, "student.contact.email").getText()).isEqualTo("typed@seu.edu.cn");
        assertThat(field(dialog, "student.contact.phone").getText()).isEqualTo("13900000000");
        assertThat(button(dialog, "student.contact.submit").isEnabled()).isTrue();
        onEdt(() -> button(dialog, "student.contact.submit").doClick());
        var retriedSave = client.await("STUDENT_UPDATE_CONTACT");
        assertThat(retriedSave.body()).isEqualTo(new UpdateStudentContactCommand(
                "student-1", "typed@seu.edu.cn", "13900000000", 9));
    }

    @Test
    void cancellationDoesNotSendOrPublish() throws Exception {
        var client = new RecordingStudentClient();
        var published = new AtomicInteger();
        UpdateContactDialog dialog = onEdt(() -> displayed(new UpdateContactDialog(null,
                new StudentClientService(client, Duration.ofSeconds(3)), profile(4, "old@seu.edu.cn", "130"),
                ignored -> published.incrementAndGet())));
        onEdt(() -> button(dialog, "student.contact.cancel").doClick());
        flushEdt();

        assertThat(client.poll()).isNull();
        assertThat(published).hasValue(0);
        assertThat(dialog.isDisplayable()).isFalse();
    }

    @Test
    void escapeCancelsWithoutSendingOrPublishing() throws Exception {
        var client = new RecordingStudentClient();
        var published = new AtomicInteger();
        UpdateContactDialog dialog = onEdt(() -> displayed(new UpdateContactDialog(null,
                new StudentClientService(client, Duration.ofSeconds(3)), profile(4, "old@seu.edu.cn", "130"),
                ignored -> published.incrementAndGet())));

        onEdt(() -> {
            JRootPane root = dialog.getRootPane();
            Object binding = root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                    .get(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0));
            assertThat(binding).isNotNull();
            Action action = root.getActionMap().get(binding);
            assertThat(action).isNotNull();
            action.actionPerformed(new ActionEvent(dialog, ActionEvent.ACTION_PERFORMED, "escape"));
        });
        flushEdt();

        assertThat(client.poll()).isNull();
        assertThat(published).hasValue(0);
        assertThat(dialog.isDisplayable()).isFalse();
    }

    @Test
    void disposalDuringInFlightSaveIgnoresLateSuccessWithoutPublishingOrMutatingUi() throws Exception {
        var client = new RecordingStudentClient();
        var published = new AtomicInteger();
        UpdateContactDialog dialog = onEdt(() -> displayed(new UpdateContactDialog(null,
                new StudentClientService(client, Duration.ofSeconds(3)), profile(4, "old@seu.edu.cn", "130"),
                ignored -> published.incrementAndGet())));
        onEdt(() -> {
            field(dialog, "student.contact.email").setText("typed@seu.edu.cn");
            button(dialog, "student.contact.submit").doClick();
        });
        var save = client.await("STUDENT_UPDATE_CONTACT");
        var lateUi = onEdt(() -> signal(label(dialog, "student.contact.error"), "text"));
        onEdt(dialog::dispose);
        awaitServiceDependent(save.response());
        client.complete(save, ResponseBody.success(profile(5, "server@seu.edu.cn", "13900000000")));
        flushEdt();
        assertNoUiEvent(lateUi);

        assertThat(published).hasValue(0);
        assertThat(field(dialog, "student.contact.email").getText()).isEqualTo("typed@seu.edu.cn");
        assertThat(dialog.isDisplayable()).isFalse();
    }

    @Test
    void disposalDuringConflictRefreshSupersedesGenerationAndIgnoresLateResponse() throws Exception {
        var client = new RecordingStudentClient();
        var published = new AtomicInteger();
        UpdateContactDialog dialog = onEdt(() -> displayed(new UpdateContactDialog(null,
                new StudentClientService(client, Duration.ofSeconds(3)), profile(4, "old@seu.edu.cn", "130"),
                ignored -> published.incrementAndGet())));
        onEdt(() -> {
            field(dialog, "student.contact.email").setText("typed@seu.edu.cn");
            field(dialog, "student.contact.phone").setText("13900000000");
            button(dialog, "student.contact.submit").doClick();
        });
        var save = client.await("STUDENT_UPDATE_CONTACT");
        var conflict = onEdt(() -> signal(label(dialog, "student.contact.error"), "text"));
        awaitServiceDependent(save.response());
        client.complete(save, ResponseBody.failure("COMMON_CONCURRENT_MODIFICATION", "数据已被修改，请刷新", null));
        await(conflict);
        onEdt(() -> button(dialog, "student.contact.refresh").doClick());
        var refresh = client.await("STUDENT_GET_CURRENT");
        var lateUi = onEdt(() -> signal(label(dialog, "student.contact.error"), "text"));
        onEdt(dialog::dispose);
        awaitServiceDependent(refresh.response());
        client.complete(refresh, ResponseBody.success(profile(9, "server@seu.edu.cn", "13700000000")));
        flushEdt();
        assertNoUiEvent(lateUi);

        assertThat(published).hasValue(0);
        assertThat(field(dialog, "student.contact.email").getText()).isEqualTo("typed@seu.edu.cn");
        assertThat(field(dialog, "student.contact.phone").getText()).isEqualTo("13900000000");
        assertThat(dialog.isDisplayable()).isFalse();
    }

    @Test
    void displayableDialogIgnoresSaveResponseFromSupersededGeneration() throws Exception {
        var client = new RecordingStudentClient();
        var published = new CountDownLatch(1);
        UpdateContactDialog dialog = onEdt(() -> displayed(new UpdateContactDialog(null,
                new StudentClientService(client, Duration.ofSeconds(3)), profile(4, "old@seu.edu.cn", "130"),
                ignored -> published.countDown())));
        onEdt(() -> {
            field(dialog, "student.contact.email").setText("typed@seu.edu.cn");
            button(dialog, "student.contact.submit").doClick();
        });
        var save = client.await("STUDENT_UPDATE_CONTACT");
        onEdt(() -> incrementGeneration(dialog));
        awaitServiceDependent(save.response());
        client.complete(save, ResponseBody.success(profile(5, "server@seu.edu.cn", "13900000000")));
        flushEdt();

        assertThat(published.await(2, TimeUnit.SECONDS)).isFalse();
        assertThat(dialog.isDisplayable()).isTrue();
        assertThat(field(dialog, "student.contact.email").getText()).isEqualTo("typed@seu.edu.cn");
        assertThat(button(dialog, "student.contact.submit").getText()).isEqualTo("正在保存");
        assertThat(button(dialog, "student.contact.submit").isEnabled()).isFalse();
    }

    @Test
    void exceptionalRefreshFailureKeepsConflictStateAndTypedValues() throws Exception {
        var client = new RecordingStudentClient();
        UpdateContactDialog dialog = dialog(client, profile(4, "old@seu.edu.cn", "130"));
        enterConflict(client, dialog);
        onEdt(() -> button(dialog, "student.contact.refresh").doClick());
        var refresh = client.await("STUDENT_GET_CURRENT");
        var failure = onEdt(() -> signal(label(dialog, "student.contact.error"), "text"));
        awaitServiceDependent(refresh.response());
        client.fail(refresh, new IllegalStateException("server.Secret"));
        await(failure);

        assertConflictRemains(dialog);
        assertThat(label(dialog, "student.contact.error").getText())
                .isEqualTo("刷新失败，请稍后重试").doesNotContain("Secret", "server");
    }

    @Test
    void ordinaryRefreshFailureKeepsConflictStateAndHidesInternalCode() throws Exception {
        var client = new RecordingStudentClient();
        UpdateContactDialog dialog = dialog(client, profile(4, "old@seu.edu.cn", "130"));
        enterConflict(client, dialog);
        onEdt(() -> button(dialog, "student.contact.refresh").doClick());
        var refresh = client.await("STUDENT_GET_CURRENT");
        var failure = onEdt(() -> signal(label(dialog, "student.contact.error"), "text"));
        awaitServiceDependent(refresh.response());
        client.complete(refresh, ResponseBody.failure("INTERNAL_STALE", "刷新被拒绝", null));
        await(failure);

        assertConflictRemains(dialog);
        assertThat(label(dialog, "student.contact.error").getText())
                .contains("刷新被拒绝").doesNotContain("INTERNAL_STALE");
    }

    private static UpdateContactDialog dialog(RecordingStudentClient client, StudentView initial) throws Exception {
        return onEdt(() -> displayed(new UpdateContactDialog(null,
                new StudentClientService(client, Duration.ofSeconds(3)), initial, ignored -> { })));
    }

    private static void enterConflict(RecordingStudentClient client, UpdateContactDialog dialog) throws Exception {
        onEdt(() -> {
            field(dialog, "student.contact.email").setText("typed@seu.edu.cn");
            field(dialog, "student.contact.phone").setText("13900000000");
            button(dialog, "student.contact.submit").doClick();
        });
        var save = client.await("STUDENT_UPDATE_CONTACT");
        var conflict = onEdt(() -> signal(label(dialog, "student.contact.error"), "text"));
        awaitServiceDependent(save.response());
        client.complete(save, ResponseBody.failure("COMMON_CONCURRENT_MODIFICATION", "数据已被修改，请刷新", null));
        await(conflict);
    }

    private static void assertConflictRemains(UpdateContactDialog dialog) {
        assertThat(field(dialog, "student.contact.email").getText()).isEqualTo("typed@seu.edu.cn");
        assertThat(field(dialog, "student.contact.phone").getText()).isEqualTo("13900000000");
        assertThat(button(dialog, "student.contact.refresh").isVisible()).isTrue();
        assertThat(button(dialog, "student.contact.refresh").isEnabled()).isTrue();
        assertThat(button(dialog, "student.contact.submit").isEnabled()).isFalse();
    }

    private static void incrementGeneration(UpdateContactDialog dialog) {
        try {
            var field = UpdateContactDialog.class.getDeclaredField("requestGeneration");
            field.setAccessible(true);
            ((AtomicLong) field.get(dialog)).incrementAndGet();
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
    }

    private static UpdateContactDialog displayed(UpdateContactDialog dialog) {
        dialog.addNotify();
        return dialog;
    }

    private static StudentView profile(long version, String email, String phone) {
        return new StudentView("student-1", "user-1", "213240001", "09024101", StudentType.UNDERGRADUATE,
                "张三", "MALE", email, phone, "major-1", "class-1", LocalDate.of(2024, 9, 1), StudentStatus.ACTIVE, version);
    }

    private static <T> T onEdt(Callable<T> work) throws Exception {
        var result = new AtomicReference<T>();
        var failure = new AtomicReference<Throwable>();
        SwingUtilities.invokeAndWait(() -> {
            try { result.set(work.call()); }
            catch (Throwable thrown) { failure.set(thrown); }
        });
        if (failure.get() != null) throw new AssertionError(failure.get());
        return result.get();
    }

    private static void onEdt(ThrowingRunnable work) throws Exception { onEdt(() -> { work.run(); return null; }); }
    private static void flushEdt() throws Exception { SwingUtilities.invokeAndWait(() -> { }); }
    private static UiSignal signal(JComponent component, String property) { return signal(component, property, new AtomicBoolean()); }
    private static UiSignal signal(JComponent component, String property, AtomicBoolean onEdt) {
        var signal = new UiSignal(onEdt);
        component.addPropertyChangeListener(property, event -> {
            signal.onEdt().set(SwingUtilities.isEventDispatchThread());
            signal.changed().countDown();
        });
        return signal;
    }
    private static void await(UiSignal signal) throws InterruptedException {
        assertThat(signal.changed().await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(signal.onEdt()).isTrue();
    }
    private static void assertNoUiEvent(UiSignal signal) throws InterruptedException {
        assertThat(signal.changed().await(2, TimeUnit.SECONDS)).isFalse();
    }
    private static void awaitServiceDependent(CompletableFuture<?> response) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (response.getNumberOfDependents() == 0 && System.nanoTime() < deadline) Thread.onSpinWait();
        assertThat(response.getNumberOfDependents()).isGreaterThan(0);
    }
    private static JTextField field(Container root, String name) { return component(root, name, JTextField.class); }
    private static JButton button(Container root, String name) { return component(root, name, JButton.class); }
    private static JLabel label(Container root, String name) { return component(root, name, JLabel.class); }
    private static <T extends Component> T component(Container root, String name, Class<T> type) {
        if (name.equals(root.getName()) && type.isInstance(root)) return type.cast(root);
        for (Component child : root.getComponents()) {
            if (name.equals(child.getName()) && type.isInstance(child)) return type.cast(child);
            if (child instanceof Container nested) {
                try { return component(nested, name, type); }
                catch (IllegalArgumentException ignored) { }
            }
        }
        throw new IllegalArgumentException("Missing component: " + name);
    }

    @FunctionalInterface private interface ThrowingRunnable { void run() throws Exception; }
    private record UiSignal(CountDownLatch changed, AtomicBoolean onEdt) {
        UiSignal(AtomicBoolean onEdt) { this(new CountDownLatch(1), onEdt); }
    }

    private static final class RecordingStudentClient implements StudentRequestClient {
        private final BlockingQueue<Call> calls = new LinkedBlockingQueue<>();

        @Override public <T extends Serializable> CompletableFuture<ResponseBody<T>> send(
                String command, Serializable body, Duration timeout) {
            CompletableFuture<ResponseBody<T>> response = new CompletableFuture<>();
            calls.add(new Call(command, body, response));
            return response;
        }

        Call await(String command) throws InterruptedException {
            Call call = calls.poll(2, TimeUnit.SECONDS);
            assertThat(call).isNotNull();
            assertThat(call.command()).isEqualTo(command);
            return call;
        }

        Call poll() throws InterruptedException { return calls.poll(150, TimeUnit.MILLISECONDS); }

        @SuppressWarnings({"unchecked", "rawtypes"})
        boolean complete(Call call, ResponseBody<?> response) { return ((CompletableFuture) call.response()).complete(response); }
        @SuppressWarnings({"unchecked", "rawtypes"})
        void fail(Call call, Throwable failure) { ((CompletableFuture) call.response()).completeExceptionally(failure); }
    }

    private record Call(String command, Serializable body, CompletableFuture<?> response) { }
}
