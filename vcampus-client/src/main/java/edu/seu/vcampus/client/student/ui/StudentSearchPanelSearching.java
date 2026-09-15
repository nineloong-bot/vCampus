package edu.seu.vcampus.client.student.ui;

import edu.seu.vcampus.client.core.network.ClientConnection;
import edu.seu.vcampus.client.core.network.ConnectionState;
import edu.seu.vcampus.client.student.service.StudentClientService;
import edu.seu.vcampus.common.paging.PageResult;
import edu.seu.vcampus.common.student.ClassView;
import edu.seu.vcampus.common.student.DepartmentView;
import edu.seu.vcampus.common.student.MajorView;
import edu.seu.vcampus.common.student.StudentSearchQuery;
import edu.seu.vcampus.common.student.StudentStatus;
import edu.seu.vcampus.common.student.StudentSummary;

/** Query execution, pagination and result rendering for the student search panel. */
abstract class StudentSearchPanelSearching extends StudentSearchPanelLoading {

    /** Creates the searching segment of the student search panel. */
    protected StudentSearchPanelSearching(StudentClientService students, ClientConnection connection,
            boolean canEdit) {
        super(students, connection, canEdit);
    }

    /**
     * Runs a fresh search using the current filter values.
     *
     * <p>Resets nothing but the request generation, so an in-flight search is
     * invalidated and its result discarded.</p>
     */
    public void search() {
        long generation = requestGeneration.incrementAndGet();
        executeSearch(generation);
    }

    void executeSearch(long generation) {
        String keyword = keywordField.getText().trim();
        String departmentId = getSelectedId(departmentCombo, DepartmentView.class);
        String majorId = getSelectedId(majorCombo, MajorView.class);
        String classId = getSelectedId(classCombo, ClassView.class);
        StudentStatus status = getSelectedStatus();
        if (currentPage < 1) currentPage = 1;
        onEdt(() -> {
            if (!active || generation != requestGeneration.get()) return;
            setSearching(true);
        });
        students.search(new StudentSearchQuery(keyword, departmentId, majorId, classId, status, currentPage, PAGE_SIZE))
                .whenComplete((body, failure) -> onEdt(() -> {
                    if (!active || generation != requestGeneration.get()) return;
                    setSearching(false);
                    if (failure != null) {
                        renderError("搜索失败，请稍后重试");
                    } else if (body != null && body.success() && body.data() != null) {
                        renderResults(body.data());
                    } else {
                        renderError(safeMessage(body));
                    }
                }));
    }

    void changePage(int delta) {
        int next = currentPage + delta;
        if (next < 1) return;
        currentPage = next;
        search();
    }

    void renderResults(PageResult<StudentSummary> page) {
        tableModel.setRowCount(0);
        currentResults.clear();
        for (StudentSummary s : page.items()) {
            tableModel.addRow(new Object[]{s.campusCardNumber(), s.studentNumber(), s.studentName(),
                    classDisplayName(s.studentNumber()), statusText(s.status())});
        }
        currentResults.addAll(page.items());
        if (page.items().isEmpty()) {
            resultsScrollPane.setVisible(false);
            resultsTable.setVisible(false);
            emptyLabel.setText("未找到匹配的学生");
            emptyLabel.setVisible(true);
        } else {
            resultsScrollPane.setVisible(true);
            resultsTable.setVisible(true);
            emptyLabel.setVisible(false);
        }
        long total = page.total();
        pageInfoLabel.setText("第" + page.page() + "页/共" + total + "条");
        prevButton.setEnabled(page.page() > 1 && connection.state() == ConnectionState.CONNECTED);
        nextButton.setEnabled((long) page.page() * page.pageSize() < total && connection.state() == ConnectionState.CONNECTED);
        statusLabel.setText("搜索完成");
        errorLabel.setText(" ");
        showPlaceholder();
    }

    void renderError(String message) {
        tableModel.setRowCount(0);
        currentResults.clear();
        resultsScrollPane.setVisible(false);
        resultsTable.setVisible(false);
        emptyLabel.setText("搜索出错");
        emptyLabel.setVisible(true);
        pageInfoLabel.setText("第0页/共0条");
        prevButton.setEnabled(false);
        nextButton.setEnabled(false);
        statusLabel.setText("搜索失败");
        errorLabel.setText(message);
        showPlaceholder();
    }

    void setSearching(boolean searching) {
        searchButton.setEnabled(!searching && connection.state() == ConnectionState.CONNECTED);
        departmentCombo.setEnabled(!searching && connection.state() == ConnectionState.CONNECTED);
        majorCombo.setEnabled(!searching && connection.state() == ConnectionState.CONNECTED);
        classCombo.setEnabled(!searching && connection.state() == ConnectionState.CONNECTED);
        statusCombo.setEnabled(!searching && connection.state() == ConnectionState.CONNECTED);
        if (searching) {
            searchButton.setText("搜索中...");
            statusLabel.setText("正在搜索...");
        } else {
            searchButton.setText("搜索");
        }
    }
}
