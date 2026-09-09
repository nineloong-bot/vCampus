package edu.seu.vcampus.client.student.majortransfer.ui;

import edu.seu.vcampus.client.core.network.ClientConnection;
import edu.seu.vcampus.client.core.ui.theme.*;
import edu.seu.vcampus.client.student.service.StudentClientService;
import edu.seu.vcampus.common.student.majortransfer.*;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Admin major-transfer management panel with batch, review, assessment, and execution views. */
public final class MajorTransferAdminPanel extends JPanel {
    private static final Color ACTION_GREEN = new Color(139, 195, 74);
    private static final Color ACTION_RED = new Color(244, 67, 54);

    private final StudentClientService students;
    private final ClientConnection connection;
    private JTabbedPane innerTabs;
    private JLabel errorLabel;

    // Batch view
    private DefaultListModel<MajorTransferBatchView> batchListModel;
    private JList<MajorTransferBatchView> batchList;

    // Review view
    private JComboBox<String> reviewBatchCombo;
    private DefaultListModel<MajorTransferApplicationView> reviewListModel;
    private JList<MajorTransferApplicationView> reviewList;

    public MajorTransferAdminPanel(StudentClientService students, ClientConnection connection) {
        super(new BorderLayout(0, UiSpacing.SPACE_2));
        this.students = Objects.requireNonNull(students);
        this.connection = Objects.requireNonNull(connection);
        setName("major-transfer.admin");
        setBackground(UiColors.BACKGROUND_PAGE);
        setBorder(new EmptyBorder(UiSpacing.SPACE_2, UiSpacing.SPACE_3, UiSpacing.SPACE_2, UiSpacing.SPACE_3));
        build();
    }

    private void build() {
        errorLabel = new JLabel(" ");
        errorLabel.setName("major-transfer.admin.error");
        errorLabel.setForeground(ACTION_RED);
        errorLabel.setFont(UiTypography.CAPTION);

        innerTabs = new JTabbedPane();
        innerTabs.setName("major-transfer.admin.tabs");
        innerTabs.addTab("批次与专业", buildBatchView());
        innerTabs.addTab("申请审核", buildReviewView());
        innerTabs.addTab("考核录取", buildAssessmentView());
        innerTabs.addTab("生效办理", buildExecutionView());

        add(innerTabs, BorderLayout.CENTER);
        add(errorLabel, BorderLayout.SOUTH);


    }

    @Override public void addNotify() { super.addNotify(); loadBatches(); }

    private JPanel buildBatchView() {
        JPanel panel = new JPanel(new BorderLayout(0, UiSpacing.SPACE_2));
        panel.setOpaque(false);

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, UiSpacing.SPACE_2, 0));
        toolbar.setOpaque(false);
        JButton createBatch = new JButton("新建批次");
        createBatch.addActionListener(e -> showBatchDialog(null));
        toolbar.add(createBatch);
        JButton refreshBatches = new JButton("刷新");
        refreshBatches.addActionListener(e -> loadBatches());
        toolbar.add(refreshBatches);
        JButton edit = new JButton("编辑批次");
        edit.addActionListener(e -> { if (batchList.getSelectedValue() != null) showBatchDialog(batchList.getSelectedValue()); });
        toolbar.add(edit);
        JButton option = new JButton("新增开放专业");
        option.setName("major-transfer.admin.add-option");
        option.addActionListener(e -> showOptionDialog()); toolbar.add(option);
        panel.add(toolbar, BorderLayout.NORTH);

        batchListModel = new DefaultListModel<>();
        batchList = new JList<>(batchListModel);
        batchList.setName("major-transfer.admin.batch-list");
        batchList.setCellRenderer(new BatchCellRenderer());
        panel.add(new JScrollPane(batchList), BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildReviewView() {
        JPanel panel = new JPanel(new BorderLayout(0, UiSpacing.SPACE_2));
        panel.setOpaque(false);

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, UiSpacing.SPACE_2, 0));
        toolbar.setOpaque(false);
        reviewBatchCombo = new JComboBox<>();
        reviewBatchCombo.setName("major-transfer.admin.review-batch");
        toolbar.add(new JLabel("批次:"));
        toolbar.add(reviewBatchCombo);
        JButton loadApps = new JButton("查询");
        loadApps.setName("major-transfer.admin.load-applications");
        loadApps.addActionListener(e -> loadApplications());
        toolbar.add(loadApps);
        panel.add(toolbar, BorderLayout.NORTH);

        reviewListModel = new DefaultListModel<>();
        reviewList = new JList<>(reviewListModel);
        reviewList.setName("major-transfer.admin.review-list");
        reviewList.setCellRenderer(new ApplicationCellRenderer());
        reviewList.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) {
                    MajorTransferApplicationView app = reviewList.getSelectedValue();
                    if (app != null) openDetail(app);
                }
            }
        });
        JPanel reviewCenter = new JPanel(new BorderLayout());
        reviewCenter.setOpaque(false);
        reviewCenter.add(new JScrollPane(reviewList), BorderLayout.CENTER);
        JLabel reviewHint = new JLabel("双击申请可查看详情并执行审核操作");
        reviewHint.setFont(UiTypography.CAPTION);
        reviewHint.setForeground(UiColors.TEXT_SECONDARY);
        reviewHint.setBorder(new EmptyBorder(4, 4, 4, 4));
        reviewCenter.add(reviewHint, BorderLayout.SOUTH);
        panel.add(reviewCenter, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildAssessmentView() {
        return workflowList("major-transfer.admin.assessment-list", java.util.Set.of(
                MajorTransferStatus.QUALIFIED, MajorTransferStatus.ASSESSED, MajorTransferStatus.PROPOSED));
    }

    private JPanel buildExecutionView() {
        return workflowList("major-transfer.admin.execution-list", java.util.Set.of(
                MajorTransferStatus.PENDING_EFFECTIVE, MajorTransferStatus.EXECUTION_FAILED, MajorTransferStatus.EFFECTIVE));
    }

    private void loadBatches() {
        students.listTransferBatches().whenComplete((response, error) ->
                SwingUtilities.invokeLater(() -> {
                    if (response != null && response.success()) {
                        batchListModel.clear();
                        reviewBatchCombo.removeAllItems();
                        for (var batch : response.data()) {
                            batchListModel.addElement(batch);
                            reviewBatchCombo.addItem(batch.batchId() + " - " + batch.batchName());
                        }
                    } else {
                        errorLabel.setText(response != null ? response.message() : "加载失败");
                    }
                }));
    }

    private void loadApplications() {
        String selected = (String) reviewBatchCombo.getSelectedItem();
        if (selected == null) return;
        String batchId = selected.split(" - ")[0];
        students.listTransferApplications(new MajorTransferApplicationQuery(batchId, null, null))
                .whenComplete((response, error) ->
                        SwingUtilities.invokeLater(() -> {
                            if (response != null && response.success()) {
                                reviewListModel.clear();
                                for (var app : response.data()) {
                                    reviewListModel.addElement(app);
                                }
                            } else {
                                errorLabel.setText(response != null ? response.message() : "加载失败");
                            }
                        }));
    }

    private void showBatchDialog(MajorTransferBatchView batch) {
        JTextField name = new JTextField(batch == null ? "" : batch.batchName(), 24);
        JComboBox<MajorTransferBatchStatus> status = new JComboBox<>(MajorTransferBatchStatus.values());
        status.setSelectedItem(batch == null ? MajorTransferBatchStatus.DRAFT : batch.status());
        JTextField start = dateField(batch == null ? Instant.now() : batch.applicationStart());
        JTextField end = dateField(batch == null ? Instant.now().plusSeconds(7 * 86400) : batch.applicationEnd());
        JTextField publicStart = dateField(batch == null ? null : batch.publicityStart());
        JTextField publicEnd = dateField(batch == null ? null : batch.publicityEnd());
        JTextField effective = dateField(batch == null ? null : batch.effectiveDate());
        JPanel form = new JPanel(new GridLayout(0, 2, 6, 6));
        form.add(new JLabel("批次名称")); form.add(name);
        form.add(new JLabel("状态")); form.add(status);
        form.add(new JLabel("报名开始")); form.add(start);
        form.add(new JLabel("报名结束")); form.add(end);
        form.add(new JLabel("公示开始")); form.add(publicStart);
        form.add(new JLabel("公示结束")); form.add(publicEnd);
        form.add(new JLabel("生效时间")); form.add(effective);
        while (JOptionPane.showConfirmDialog(this, form, "批次配置（北京时间）",
                JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION) {
            try {
                var command = new SaveMajorTransferBatchCommand(batch == null ? null : batch.batchId(),
                        name.getText().trim(), (MajorTransferBatchStatus) status.getSelectedItem(),
                        parseDate(start), parseDate(end), parseDate(publicStart), parseDate(publicEnd),
                        parseDate(effective), batch == null ? 0 : batch.rowVersion());
                students.saveTransferBatch(command).whenComplete((response, error) -> SwingUtilities.invokeLater(() -> {
                    if (response != null && response.success()) loadBatches();
                    else errorLabel.setText(response == null ? "保存失败" : response.message());
                }));
                return;
            } catch (RuntimeException error) {
                JOptionPane.showMessageDialog(this, "请检查日期和字段：" + error.getMessage());
            }
        }
    }

    private static JTextField dateField(Instant value) {
        var format = java.time.format.DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm");
        JTextField field = new JTextField(value == null ? "" : format.format(value.atZone(ZoneId.of("Asia/Shanghai"))), 20);
        field.setToolTipText("北京时间，格式：2026-09-10 09:00");
        return field;
    }

    private static Instant parseDate(JTextField field) {
        if (field.getText().isBlank()) return null;
        return java.time.LocalDateTime.parse(field.getText().trim(),
                java.time.format.DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm")
                        .withResolverStyle(java.time.format.ResolverStyle.STRICT))
                .atZone(ZoneId.of("Asia/Shanghai")).toInstant();
    }

    private void showOptionDialog() {
        var batch = batchList.getSelectedValue();
        if (batch == null) { errorLabel.setText("请先选择批次"); return; }
        students.listDepartments(true).whenComplete((departments, failure) -> SwingUtilities.invokeLater(() -> {
            if (departments == null || !departments.success()) { errorLabel.setText("学院加载失败"); return; }
            JComboBox<edu.seu.vcampus.common.student.DepartmentView> department = new JComboBox<>();
            departments.data().forEach(department::addItem);
            JComboBox<edu.seu.vcampus.common.student.MajorView> major = new JComboBox<>();
            department.addActionListener(event -> {
                major.removeAllItems();
                var selected = (edu.seu.vcampus.common.student.DepartmentView) department.getSelectedItem();
                if (selected == null) return;
                students.listMajors(selected.departmentId()).whenComplete((majors, error) -> SwingUtilities.invokeLater(() -> {
                    if (department.getSelectedItem() != selected) return;
                    if (majors != null && majors.success()) majors.data().forEach(major::addItem);
                }));
            });
            if (department.getItemCount() > 0) department.setSelectedIndex(0);
            JTextField grades = new JTextField("2024,2025");
            grades.setToolTipText("允许申请的入学年份，以英文逗号分隔");
            JSpinner quota = new JSpinner(new SpinnerNumberModel(10, 0, 10000, 1));
            JSpinner interviews = new JSpinner(new SpinnerNumberModel(20, 0, 10000, 1));
            JSpinner writtenWeight = new JSpinner(new SpinnerNumberModel(60, 0, 100, 1));
            JSpinner writtenPass = new JSpinner(new SpinnerNumberModel(60, 0, 100, 1));
            JSpinner interviewPass = new JSpinner(new SpinnerNumberModel(60, 0, 100, 1));
            JCheckBox exempt = new JCheckBox("学困生不占普通名额");
            JTextField requirements = new JTextField();
            JPanel form = new JPanel(new GridLayout(0, 2, 6, 6));
            form.add(new JLabel("学院")); form.add(department);
            form.add(new JLabel("专业")); form.add(major);
            form.add(new JLabel("允许年级")); form.add(grades);
            form.add(new JLabel("接收名额")); form.add(quota);
            form.add(new JLabel("面试名额")); form.add(interviews);
            form.add(new JLabel("笔试权重%（其余为面试）")); form.add(writtenWeight);
            form.add(new JLabel("笔试合格线")); form.add(writtenPass);
            form.add(new JLabel("面试合格线")); form.add(interviewPass);
            form.add(new JLabel("学困生规则")); form.add(exempt);
            form.add(new JLabel("附加要求")); form.add(requirements);
            while (JOptionPane.showConfirmDialog(this, form, "新增开放专业", JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION) {
                try {
                    var target = (edu.seu.vcampus.common.student.MajorView) major.getSelectedItem();
                    if (target == null) throw new IllegalArgumentException("请选择专业");
                    int weight = (Integer) writtenWeight.getValue();
                    var command = new SaveMajorTransferOptionCommand(null, batch.batchId(), target.majorId(),
                            grades.getText().trim(), (Integer) quota.getValue(), (Integer) interviews.getValue(),
                            ((Number) writtenPass.getValue()).doubleValue(), ((Number) interviewPass.getValue()).doubleValue(),
                            weight, 100 - weight, exempt.isSelected(), requirements.getText(), true, 0);
                    students.saveTransferOption(command).whenComplete((response, error) -> SwingUtilities.invokeLater(() ->
                            errorLabel.setText(response != null && response.success() ? "开放专业已保存" :
                                    response == null ? "保存失败" : response.message())));
                    return;
                } catch (RuntimeException error) { JOptionPane.showMessageDialog(this, error.getMessage()); }
            }
        }));
    }

    private JPanel workflowList(String componentName, java.util.Set<MajorTransferStatus> statuses) {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        JComboBox<MajorTransferBatchView> batches = new JComboBox<>();
        batches.setRenderer(new BatchCellRenderer());
        DefaultListModel<MajorTransferApplicationView> model = new DefaultListModel<>();
        JList<MajorTransferApplicationView> list = new JList<>(model);
        list.setName(componentName); list.setCellRenderer(new ApplicationCellRenderer());
        JButton refresh = new JButton("加载批次");
        refresh.addActionListener(e -> students.listTransferBatches().whenComplete((response, error) ->
                SwingUtilities.invokeLater(() -> {
                    if (response == null || !response.success()) { errorLabel.setText("批次加载失败"); return; }
                    batches.removeAllItems(); response.data().forEach(batches::addItem);
                })));
        batches.addActionListener(e -> {
            var batch = (MajorTransferBatchView) batches.getSelectedItem();
            model.clear(); if (batch == null) return;
            students.listTransferApplications(new MajorTransferApplicationQuery(batch.batchId(), null, null))
                    .whenComplete((response, error) -> SwingUtilities.invokeLater(() -> {
                        if (batches.getSelectedItem() != batch) return;
                        if (response != null && response.success())
                            response.data().stream().filter(a -> statuses.contains(a.status())).forEach(model::addElement);
                        else errorLabel.setText("申请加载失败");
                    }));
        });
        JButton detail = new JButton("查看并办理");
        detail.addActionListener(e -> { if (list.getSelectedValue() != null) openDetail(list.getSelectedValue()); });
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        toolbar.add(refresh); toolbar.add(batches); toolbar.add(detail);
        panel.add(toolbar, BorderLayout.NORTH); panel.add(new JScrollPane(list));
        return panel;
    }

    private void openDetail(MajorTransferApplicationView app) {
        students.getTransferApplication(app.applicationId()).whenComplete((response, error) ->
                SwingUtilities.invokeLater(() -> {
                    if (response != null && response.success()) showApplicationDetail(response.data());
                    else errorLabel.setText(response == null ? "加载失败" : response.message());
                }));
    }

    private void generateProposal(MajorTransferApplicationView app) {
        students.listTransferOptions(app.batchId()).whenComplete((response, error) -> SwingUtilities.invokeLater(() -> {
            if (response == null || !response.success()) { errorLabel.setText("专业配置加载失败"); return; }
            var option = response.data().stream().filter(o -> o.optionId().equals(app.optionId())).findFirst().orElseThrow();
            if (JOptionPane.showConfirmDialog(this, "将按成绩和名额确定该专业所有申请的结果，未入选者将被驳回。确认生成？",
                    "生成拟录取名单", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
            students.generateTransferProposal(new GenerateMajorTransferProposalCommand(option.optionId(), option.rowVersion()))
                    .whenComplete((result, failure) -> SwingUtilities.invokeLater(() -> {
                        if (result == null || !result.success()) { errorLabel.setText(result == null ? "生成失败" : result.message()); return; }
                        StringBuilder text = new StringBuilder();
                        result.data().applicants().forEach(a -> text.append(a.studentName()).append("  ")
                                .append(a.finalScore()).append(a.proposed() ? "  拟录取" : "  未录取").append("\n"));
                        JOptionPane.showMessageDialog(this, new JScrollPane(new JTextArea(text.toString(), 14, 40)), "拟录取结果", JOptionPane.INFORMATION_MESSAGE);
                        loadApplications();
                    }));
        }));
    }

    private void showApplicationDetail(MajorTransferApplicationView app) {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setBorder(new EmptyBorder(8, 8, 8, 8));

        // ── Top: info fields ──
        JPanel infoPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(3, 6, 3, 6);
        gbc.anchor = GridBagConstraints.WEST;
        int row = 0;

        addInfoRow(infoPanel, gbc, row++, "学生姓名:", app.studentName());
        addInfoRow(infoPanel, gbc, row++, "学号:", app.fromStudentNumber());
        addInfoRow(infoPanel, gbc, row++, "原学院:", app.fromDepartmentName());
        addInfoRow(infoPanel, gbc, row++, "原专业:", app.fromMajorName());
        addInfoRow(infoPanel, gbc, row++, "原班级:", app.fromClassName());
        addInfoRow(infoPanel, gbc, row++, "目标学院:", app.targetDepartmentName());
        addInfoRow(infoPanel, gbc, row++, "目标专业:", app.targetMajorName());
        addInfoRow(infoPanel, gbc, row++, "申请类型:", app.applicationType() == MajorTransferApplicationType.ORDINARY ? "普通转专业" : "学困生转专业");
        addInfoRow(infoPanel, gbc, row++, "当前状态:", statusLabel(app.status()));
        for (var attachment : app.attachments()) {
            gbc.gridx = 0; gbc.gridy = row++; gbc.gridwidth = 2;
            JButton download = new JButton("下载材料：" + attachment.fileName());
            download.addActionListener(e -> students.getTransferAttachment(attachment.attachmentId())
                    .whenComplete((response, error) -> SwingUtilities.invokeLater(() -> {
                        if (response == null || !response.success()) { errorLabel.setText("附件下载失败"); return; }
                        JFileChooser chooser = new JFileChooser();
                        String name = response.data().fileName().replace('\\', '/');
                        chooser.setSelectedFile(new java.io.File(name.substring(name.lastIndexOf('/') + 1)));
                        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
                        java.nio.file.Path target = chooser.getSelectedFile().toPath();
                        if (java.nio.file.Files.exists(target) && JOptionPane.showConfirmDialog(this,
                                "文件已存在，是否替换？", "保存材料", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
                        java.util.concurrent.CompletableFuture.runAsync(() -> {
                            try { java.nio.file.Files.write(target, response.data().content()); }
                            catch (java.io.IOException ex) { throw new java.io.UncheckedIOException(ex); }
                        }).whenComplete((ignored, failure) -> SwingUtilities.invokeLater(() ->
                                errorLabel.setText(failure == null ? "材料已保存" : "保存失败")));
                    })));
            infoPanel.add(download, gbc); gbc.gridwidth = 1;
        }

        if (app.writtenScore() != null || app.interviewScore() != null || app.finalScore() != null) {
            String scores = "";
            if (app.writtenScore() != null) scores += "笔试 " + app.writtenScore();
            if (app.interviewScore() != null) scores += "  面试 " + app.interviewScore();
            if (app.finalScore() != null) scores += "  总分 " + app.finalScore();
            addInfoRow(infoPanel, gbc, row++, "考核成绩:", scores.trim());
        }

        // Reason
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0; gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        JLabel reasonLabel = new JLabel("申请理由:");
        reasonLabel.setFont(UiTypography.BODY);
        infoPanel.add(reasonLabel, gbc);
        gbc.gridx = 1; gbc.weightx = 1; gbc.fill = GridBagConstraints.BOTH;
        JTextArea reasonArea = new JTextArea(app.reason() != null ? app.reason() : "(未填写)");
        reasonArea.setEditable(false);
        reasonArea.setFont(UiTypography.BODY);
        reasonArea.setLineWrap(true);
        reasonArea.setWrapStyleWord(true);
        reasonArea.setRows(3);
        infoPanel.add(new JScrollPane(reasonArea), gbc);
        row++;

        JScrollPane infoScroll = new JScrollPane(infoPanel);
        infoScroll.setBorder(BorderFactory.createTitledBorder("申请信息"));

        // ── Reviews ──
        StringBuilder reviewSb = new StringBuilder();
        if (app.reviews().isEmpty()) {
            reviewSb.append("(暂无审核记录)\n");
        }
        for (var review : app.reviews()) {
            reviewSb.append(reviewStageLabel(review.reviewStage())).append(": ")
                    .append(review.decision() == MajorTransferDecision.APPROVE ? "通过" : "驳回");
            if (review.comment() != null) reviewSb.append(" — ").append(review.comment());
            reviewSb.append("\n");
        }
        JTextArea reviewArea = new JTextArea(reviewSb.toString());
        reviewArea.setEditable(false);
        reviewArea.setFont(UiTypography.CAPTION);
        reviewArea.setLineWrap(true);
        reviewArea.setWrapStyleWord(true);
        reviewArea.setToolTipText(reviewSb.toString());
        JScrollPane reviewScroll = new JScrollPane(reviewArea);
        reviewScroll.setBorder(BorderFactory.createTitledBorder("审核记录"));

        // Split: info area ~70%, review area ~30%, both expand to fill
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, infoScroll, reviewScroll);
        splitPane.setResizeWeight(0.7);
        splitPane.setOneTouchExpandable(true);
        panel.add(splitPane, BorderLayout.CENTER);

        // ── Bottom: status hint + action buttons ──
        JLabel hint = new JLabel("当前状态: " + statusLabel(app.status()));
        hint.setFont(UiTypography.BODY);
        hint.setForeground(ACTION_GREEN);
        hint.setBorder(new EmptyBorder(4, 6, 0, 6));

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        JLabel actionStatus = new JLabel(" ");
        actionStatus.setFont(UiTypography.CAPTION);

        if (app.status() == MajorTransferStatus.SUBMITTED) {
            hint.setText("等待原学院审核 — 学生已提交，可进行原学院审核");
            JButton approveBtn = new JButton("原学院审核通过");
            approveBtn.setName("major-transfer.admin.source-review");
            approveBtn.addActionListener(e -> {
                approveBtn.setEnabled(false);
                actionStatus.setText("正在处理...");
                reviewSource(app, MajorTransferDecision.APPROVE, null, actionStatus);
            });
            JButton rejectBtn = new JButton("驳回");
            rejectBtn.addActionListener(e -> {
                String comment = JOptionPane.showInputDialog(this, "驳回原因:");
                if (comment != null && !comment.isBlank()) {
                    rejectBtn.setEnabled(false);
                    actionStatus.setText("正在处理...");
                    reviewSource(app, MajorTransferDecision.REJECT, comment, actionStatus);
                }
            });
            actions.add(approveBtn);
            actions.add(rejectBtn);
        } else if (app.status() == MajorTransferStatus.SOURCE_APPROVED) {
            hint.setText("原学院已通过 — 等待转入学院资格审核");
            JButton qualBtn = new JButton("资格审核通过");
            qualBtn.setName("major-transfer.admin.qualification-review");
            qualBtn.addActionListener(e -> {
                qualBtn.setEnabled(false);
                actionStatus.setText("正在处理...");
                reviewQualification(app, MajorTransferDecision.APPROVE, null, actionStatus);
            });
            JButton rejectBtn = new JButton("驳回");
            rejectBtn.addActionListener(e -> {
                String comment = JOptionPane.showInputDialog(this, "驳回原因:");
                if (comment != null && !comment.isBlank()) {
                    rejectBtn.setEnabled(false);
                    actionStatus.setText("正在处理...");
                    reviewQualification(app, MajorTransferDecision.REJECT, comment, actionStatus);
                }
            });
            actions.add(qualBtn);
            actions.add(rejectBtn);
        } else if (app.status() == MajorTransferStatus.QUALIFIED) {
            hint.setText("资格审核已通过 — 可录入笔试和面试成绩");
            JButton scoreBtn = new JButton("录入成绩");
            scoreBtn.setName("major-transfer.admin.record-score");
            scoreBtn.addActionListener(e -> showScoreDialog(app));
            actions.add(scoreBtn);
        } else if (app.status() == MajorTransferStatus.ASSESSED) {
            hint.setText("已完成考核，可按专业生成拟录取名单");
            JButton proposal = new JButton("生成拟录取名单");
            proposal.setName("major-transfer.admin.generate-proposal");
            proposal.addActionListener(e -> generateProposal(app));
            actions.add(proposal);
        } else if (app.status() == MajorTransferStatus.PROPOSED) {
            hint.setText("已拟录取 — 可进行终审");
            JButton finalizeBtn = new JButton("终审通过");
            finalizeBtn.setName("major-transfer.admin.finalize");
            finalizeBtn.addActionListener(e -> {
                finalizeBtn.setEnabled(false);
                actionStatus.setText("正在处理...");
                finalize(app, actionStatus);
            });
            actions.add(finalizeBtn);
        } else if (app.status() == MajorTransferStatus.PENDING_EFFECTIVE
                || app.status() == MajorTransferStatus.EXECUTION_FAILED) {
            hint.setText("待生效 — 请选择目标班级并执行转专业");
            JButton execBtn = new JButton("执行转专业");
            execBtn.setName("major-transfer.admin.execute");
            execBtn.addActionListener(e -> showExecuteDialog(app));
            actions.add(execBtn);
        } else if (app.status() == MajorTransferStatus.DRAFT) {
            hint.setText("草稿状态 — 学生尚未提交，暂不可审核");
            hint.setForeground(ACTION_RED);
        } else if (app.status() == MajorTransferStatus.EFFECTIVE) {
            hint.setText("已生效 — 转专业已完成");
        } else if (app.status() == MajorTransferStatus.REJECTED) {
            hint.setText("已驳回");
            hint.setForeground(ACTION_RED);
        } else if (app.status() == MajorTransferStatus.CANCELLED) {
            hint.setText("已取消");
        }

        if (MajorTransferStateMachine.adminMayCancel(app.status())) {
            JButton cancelBtn = new JButton("取消申请");
            cancelBtn.addActionListener(e -> {
                String reason = JOptionPane.showInputDialog(this, "取消原因:");
                if (reason != null && !reason.isBlank()) cancel(app, reason);
            });
            actions.add(cancelBtn);
        }

        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.add(hint, BorderLayout.NORTH);
        bottomPanel.add(actions, BorderLayout.CENTER);
        bottomPanel.add(actionStatus, BorderLayout.SOUTH);

        // Assemble
        JPanel centerPanel = new JPanel(new BorderLayout(0, 4));
        centerPanel.add(infoScroll, BorderLayout.CENTER);
        centerPanel.add(reviewScroll, BorderLayout.SOUTH);
        panel.add(centerPanel, BorderLayout.CENTER);
        panel.add(bottomPanel, BorderLayout.SOUTH);

        // Use JDialog with pack() so it auto-sizes to content
        Window owner = SwingUtilities.getWindowAncestor(this);
        JDialog dialog = owner instanceof Frame ? new JDialog((Frame) owner, "申请详情 — " + app.studentName(), true)
                : new JDialog(owner, "申请详情 — " + app.studentName(), java.awt.Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.getContentPane().add(panel);
        dialog.setMinimumSize(new Dimension(480, 360));
        dialog.pack();
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
        loadApplications();
    }

    private static void addInfoRow(JPanel panel, GridBagConstraints gbc, int row, String label, String value) {
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0; gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.WEST;
        JLabel lbl = new JLabel(label);
        lbl.setFont(UiTypography.BODY);
        panel.add(lbl, gbc);
        gbc.gridx = 1; gbc.weightx = 1; gbc.fill = GridBagConstraints.HORIZONTAL;
        JLabel val = new JLabel(value != null ? value : "");
        val.setFont(UiTypography.BODY);
        // Tooltip: show full text on hover, even when truncated
        if (value != null && !value.isEmpty()) {
            val.setToolTipText(value);
        }
        panel.add(val, gbc);
    }

    private String statusLabel(MajorTransferStatus status) {
        return switch (status) {
            case DRAFT -> "草稿(未提交)";
            case SUBMITTED -> "已提交(待原学院审核)";
            case SOURCE_APPROVED -> "原学院审核通过(待转入学院审核)";
            case QUALIFIED -> "资格审核通过(待录入成绩)";
            case ASSESSED -> "已考核";
            case PROPOSED -> "拟录取(待终审)";
            case PENDING_EFFECTIVE -> "待生效";
            case EFFECTIVE -> "已生效";
            case REJECTED -> "已驳回";
            case CANCELLED -> "已取消";
            case EXECUTION_FAILED -> "执行失败";
        };
    }

    private String reviewStageLabel(MajorTransferReviewStage stage) {
        return switch (stage) {
            case SOURCE_REVIEW -> "原学院审核";
            case QUALIFICATION_REVIEW -> "转入学院审核";
            case ASSESSMENT -> "成绩录入";
            case PROPOSAL -> "拟录取生成";
            case FINAL_APPROVAL -> "终审";
            case EXECUTION -> "执行";
        };
    }

    private void reviewSource(MajorTransferApplicationView app, MajorTransferDecision decision,
                              String comment, JLabel statusLabel) {
        JCheckBox academic = new JCheckBox("已核实学籍审核合格");
        JCheckBox conduct = new JCheckBox("已核实无禁止性违纪");
        JCheckBox admission = new JCheckBox("已核实招生类别允许转专业");
        if (decision == MajorTransferDecision.APPROVE) {
            JPanel checks = new JPanel(new GridLayout(0, 1));
            checks.add(academic); checks.add(conduct); checks.add(admission);
            if (JOptionPane.showConfirmDialog(this, checks, "原学院资格核实", JOptionPane.OK_CANCEL_OPTION)
                    != JOptionPane.OK_OPTION) { statusLabel.setText("已取消，请重新打开详情办理"); return; }
        }
        var cmd = new ReviewMajorTransferSourceCommand(app.applicationId(), decision,
                academic.isSelected(), conduct.isSelected(), admission.isSelected(), comment, app.applicationVersion());
        students.reviewTransferSource(cmd).whenComplete((response, error) ->
                SwingUtilities.invokeLater(() -> {
                    if (response != null && response.success()) {
                        statusLabel.setText("审核完成");
                        statusLabel.setForeground(ACTION_GREEN);
                        loadApplications();
                    } else {
                        statusLabel.setText("失败: " + (response != null ? response.message() : "网络错误"));
                        statusLabel.setForeground(ACTION_RED);
                        errorLabel.setText(response != null ? response.message() : "审核失败");
                    }
                }));
    }

    private void reviewQualification(MajorTransferApplicationView app,
                                     MajorTransferDecision decision, String comment,
                                     JLabel statusLabel) {
        var cmd = new ReviewMajorTransferQualificationCommand(app.applicationId(), decision,
                comment, app.applicationVersion());
        students.reviewTransferQualification(cmd).whenComplete((response, error) ->
                SwingUtilities.invokeLater(() -> {
                    if (response != null && response.success()) {
                        statusLabel.setText("审核完成");
                        statusLabel.setForeground(ACTION_GREEN);
                        loadApplications();
                    } else {
                        statusLabel.setText("失败: " + (response != null ? response.message() : "网络错误"));
                        statusLabel.setForeground(ACTION_RED);
                        errorLabel.setText(response != null ? response.message() : "审核失败");
                    }
                }));
    }

    private void showScoreDialog(MajorTransferApplicationView app) {
        JTextField writtenField = new JTextField(10);
        JTextField interviewField = new JTextField(10);
        JPanel panel = new JPanel(new GridLayout(0, 2, 4, 4));
        panel.add(new JLabel("笔试成绩:"));
        panel.add(writtenField);
        panel.add(new JLabel("面试成绩:"));
        panel.add(interviewField);
        int result = JOptionPane.showConfirmDialog(this, panel, "录入成绩", JOptionPane.OK_CANCEL_OPTION);
        if (result == JOptionPane.OK_OPTION) {
            try {
            BigDecimal written = writtenField.getText().isBlank() ? null
                    : new BigDecimal(writtenField.getText().trim());
            BigDecimal interview = interviewField.getText().isBlank() ? null
                    : new BigDecimal(interviewField.getText().trim());
            var cmd = new RecordMajorTransferScoreCommand(app.applicationId(),
                    written, interview, app.applicationVersion());
            students.recordTransferScore(cmd).whenComplete((response, error) ->
                    SwingUtilities.invokeLater(() -> {
                        if (response != null && response.success()) loadApplications();
                        else errorLabel.setText(response != null ? response.message() : "录入失败");
                    }));
            } catch (NumberFormatException error) { JOptionPane.showMessageDialog(this, "成绩请输入0至100的数字"); }
        }
    }

    private void showExecuteDialog(MajorTransferApplicationView app) {
        students.listClasses(app.targetMajorId()).whenComplete((response, error) ->
                SwingUtilities.invokeLater(() -> {
                    if (response == null || !response.success()) {
                        errorLabel.setText("加载班级失败");
                        return;
                    }
                    JComboBox<String> classCombo = new JComboBox<>();
                    for (var cls : response.data()) {
                        if (cls.active()) classCombo.addItem(cls.classId() + " - " + cls.name());
                    }
                    JPanel panel = new JPanel(new GridLayout(0, 2, 4, 4));
                    panel.add(new JLabel("目标班级:"));
                    panel.add(classCombo);
                    int result = JOptionPane.showConfirmDialog(this, panel, "执行转专业",
                            JOptionPane.OK_CANCEL_OPTION);
                    if (result == JOptionPane.OK_OPTION) {
                        String selected = (String) classCombo.getSelectedItem();
                        if (selected == null) return;
                        String classId = selected.split(" - ")[0];
                        var cmd = new ExecuteMajorTransferCommand(app.applicationId(),
                                classId, app.applicationVersion());
                        students.executeTransfer(cmd).whenComplete((resp, err) ->
                                SwingUtilities.invokeLater(() -> {
                                    if (resp != null && resp.success()) loadApplications();
                                    else errorLabel.setText(resp != null ? resp.message() : "执行失败");
                                }));
                    }
                }));
    }

    private void finalize(MajorTransferApplicationView app, JLabel statusLabel) {
        var cmd = new FinalizeMajorTransferCommand(app.applicationId(), app.applicationVersion());
        students.finalizeTransfer(cmd).whenComplete((response, error) ->
                SwingUtilities.invokeLater(() -> {
                    if (response != null && response.success()) {
                        statusLabel.setText("终审通过");
                        statusLabel.setForeground(ACTION_GREEN);
                        loadApplications();
                    } else {
                        statusLabel.setText("失败: " + (response != null ? response.message() : "网络错误"));
                        statusLabel.setForeground(ACTION_RED);
                        errorLabel.setText(response != null ? response.message() : "终审失败");
                    }
                }));
    }

    private void cancel(MajorTransferApplicationView app, String reason) {
        var cmd = new CancelMajorTransferCommand(app.applicationId(), reason, app.applicationVersion());
        students.cancelTransfer(cmd).whenComplete((response, error) ->
                SwingUtilities.invokeLater(() -> {
                    if (response != null && response.success()) loadApplications();
                    else errorLabel.setText(response != null ? response.message() : "取消失败");
                }));
    }

    private static class BatchCellRenderer extends DefaultListCellRenderer {
        @Override public Component getListCellRendererComponent(JList<?> list, Object value,
                int index, boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof MajorTransferBatchView batch) {
                setText(batch.batchName() + " [" + batch.status() + "]");
            }
            return this;
        }
    }

    private static class ApplicationCellRenderer extends DefaultListCellRenderer {
        @Override public Component getListCellRendererComponent(JList<?> list, Object value,
                int index, boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof MajorTransferApplicationView app) {
                String score = app.finalScore() != null ? " 总分:" + app.finalScore() : "";
                String statusText = switch (app.status()) {
                    case DRAFT -> "[草稿]";
                    case SUBMITTED -> "[待原学院审核]";
                    case SOURCE_APPROVED -> "[待转入学院审核]";
                    case QUALIFIED -> "[待录入成绩]";
                    case PROPOSED -> "[拟录取]";
                    case PENDING_EFFECTIVE -> "[待生效]";
                    case EFFECTIVE -> "[已生效]";
                    case REJECTED -> "[已驳回]";
                    case CANCELLED -> "[已取消]";
                    case EXECUTION_FAILED -> "[执行失败]";
                    case ASSESSED -> "[已考核]";
                };
                setText(statusText + " " + app.studentName() + " "
                        + app.fromMajorName() + " → " + app.targetMajorName() + score);
                if (!isSelected) {
                    setForeground(switch (app.status()) {
                        case DRAFT -> Color.GRAY;
                        case SUBMITTED -> new Color(33, 150, 243);
                        case SOURCE_APPROVED, QUALIFIED -> new Color(255, 152, 0);
                        case PROPOSED, PENDING_EFFECTIVE -> new Color(76, 175, 80);
                        case EFFECTIVE -> new Color(139, 195, 74);
                        case REJECTED, CANCELLED, EXECUTION_FAILED -> new Color(244, 67, 54);
                        case ASSESSED -> Color.BLACK;
                    });
                }
            }
            return this;
        }
    }
}
