package edu.seu.vcampus.client.course.ui;

import edu.seu.vcampus.common.course.CourseCatalogQuery;
import edu.seu.vcampus.common.user.UserSummary;

import javax.swing.JComboBox;
import javax.swing.SwingUtilities;
import java.awt.Window;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/** Reference loading and combo installation for the offering editor segments. */
abstract class OfferingEditorDialogLoading extends OfferingEditorDialogBase {

    OfferingEditorDialogLoading(Window owner, CourseUiGateway gateway, edu.seu.vcampus.common.course.OfferingSummary existing,
            Runnable onSaved) {
        super(owner, gateway, existing, onSaved);
    }

    void loadReferences() {
        referenceReady = false;
        save.setEnabled(false);
        retry.setEnabled(false);
        referenceStatus.setText("正在加载学期、课程和教师，请稍候…");
        error.setText(" ");
        long request = ++referenceSequence;
        String courseSearch = courseKeyword.getText().trim();
        String teacherSearch = teacherKeyword.getText().trim();
        if (courseSearch.isEmpty() && existing != null) courseSearch = existing.courseCode();

        CompletableFuture<String> selectedTerm = existing == null
                ? gateway.currentTermId() : CompletableFuture.completedFuture(existing.termId());
        CompletableFuture<Optional<UserSummary>> existingTeacher = existing == null
                || resolvedExistingTeacher != null
                ? CompletableFuture.completedFuture(Optional.empty())
                : gateway.resolveTeacher(existing.teacherUserId());
        CompletableFuture<ReferenceData> loaded = gateway.listTerms()
                .thenCombine(selectedTerm, TermAndCurrent::new)
                .thenCombine(gateway.searchCatalog(new CourseCatalogQuery(courseSearch, true, 0, 100)),
                        (termData, courses) -> new PartialReferenceData(termData, courses))
                .thenCombine(gateway.searchTeachers(teacherSearch),
                        (partial, teachers) -> new ReferenceData(
                                partial.termData(), partial.courses(), teachers, Optional.empty()))
                .thenCombine(existingTeacher, (data, resolved) -> new ReferenceData(
                        data.termData(), data.courses(), data.teachers(), resolved));
        loaded.whenComplete((data, failure) -> SwingUtilities.invokeLater(() -> {
            if (!active || referenceSequence != request) return;
            if (failure != null) {
                referenceStatus.setText("参考数据加载失败，请重试");
                retry.setEnabled(true);
                return;
            }
            installReferences(data);
            referenceReady = term.getSelectedItem() != null
                    && course.getSelectedItem() != null && teacher.getSelectedItem() != null;
            referenceStatus.setText(referenceReady
                    ? "参考数据已就绪" : "请选择有结果的学期、课程和教师");
            save.setEnabled(referenceReady);
        }));
    }

    void installReferences(ReferenceData data) {
        data.existingTeacher().ifPresent(value -> resolvedExistingTeacher =
                new OfferingReferenceChoice(value.userId(), value.loginId()));
        OfferingReferenceChoice currentTerm = selectedChoice(term);
        OfferingReferenceChoice currentCourse = selectedChoice(course);
        OfferingReferenceChoice currentTeacher = selectedChoice(teacher);
        String desiredTerm = currentTerm == null && existing != null ? existing.termId() : id(currentTerm);
        String desiredCourse = currentCourse == null && existing != null ? existing.courseId() : id(currentCourse);
        String desiredTeacher = currentTeacher == null && existing != null ? existing.teacherUserId() : id(currentTeacher);
        if (desiredTerm == null) desiredTerm = data.termData().currentTermId();

        List<OfferingReferenceChoice> terms = data.termData().terms().stream()
                .map(value -> new OfferingReferenceChoice(value.termId(), value.termName() + " · " + value.termCode()))
                .toList();
        List<OfferingReferenceChoice> courses = data.courses().items().stream()
                .map(value -> new OfferingReferenceChoice(value.courseId(), value.courseCode() + " · " + value.courseName()))
                .toList();
        List<OfferingReferenceChoice> teachers = data.teachers().items().stream()
                .map(value -> new OfferingReferenceChoice(value.userId(), value.loginId()))
                .toList();
        installChoices(term, terms, desiredTerm,
                currentTerm != null ? currentTerm : existing == null
                        ? null : new OfferingReferenceChoice(existing.termId(), existing.termId()));
        installChoices(course, courses, desiredCourse,
                currentCourse != null ? currentCourse : existing == null ? null
                        : new OfferingReferenceChoice(existing.courseId(),
                                existing.courseCode() + " · " + existing.courseName()));
        installChoices(teacher, teachers, desiredTeacher,
                currentTeacher != null ? currentTeacher : existing == null
                        ? null : resolvedExistingTeacher);
    }

    static void installChoices(JComboBox<OfferingReferenceChoice> combo,
                                       List<OfferingReferenceChoice> values,
                                       String desiredId,
                                       OfferingReferenceChoice fallback) {
        List<OfferingReferenceChoice> choices = new ArrayList<>(values);
        boolean found = desiredId != null && choices.stream().anyMatch(value -> desiredId.equals(value.id()));
        if (!found && fallback != null && desiredId.equals(fallback.id())) choices.add(0, fallback);
        combo.removeAllItems();
        choices.forEach(combo::addItem);
        selectId(combo, desiredId);
    }

    static OfferingReferenceChoice selectedChoice(JComboBox<OfferingReferenceChoice> combo) {
        Object selected = combo.getSelectedItem();
        return selected instanceof OfferingReferenceChoice choice ? choice : null;
    }

    static String id(OfferingReferenceChoice choice) {
        return choice == null ? null : choice.id();
    }

    static void selectId(JComboBox<OfferingReferenceChoice> combo, String id) {
        if (id == null) return;
        for (int index = 0; index < combo.getItemCount(); index++) {
            if (id.equals(combo.getItemAt(index).id())) {
                combo.setSelectedIndex(index);
                return;
            }
        }
    }

    static String requiredChoice(JComboBox<OfferingReferenceChoice> combo, String message) {
        Object selected = combo.getSelectedItem();
        if (!(selected instanceof OfferingReferenceChoice choice)) throw new IllegalArgumentException(message);
        return choice.id();
    }
}
