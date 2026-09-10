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
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Admin major-transfer management panel with left-right split layout. */
public final class MajorTransferAdminPanel extends JPanel {
    private static final Color ACTION_GREEN = new Color(139, 195, 74);
    private static final Color ACTION_RED = new Color(244, 67, 54);

    private final StudentClientService students;
    private final ClientConnection connection;
    private final JLabel errorLabel = new JLabel(" ");

    // Left panel
    private DefaultListModel<MajorTransferBatchView> batchListModel;
    private JList<MajorTransferBatchView> batchList;
    private DefaultListModel<MajorTransferApplicationView> appListModel;
    private JList<MajorTransferApplicationView> appList;

    // Right panel
    private JPanel detailPanel;
    private JLabel detailHint;
    private JPanel actionsPanel;
    private JLabel actionStatus;
    private JPanel infoPanel;
    private JTextArea reviewArea;

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
        errorLabel.setName("major-transfer.admin.error");
        errorLabel.setForeground(ACTION_RED);
        errorLabel.setFont(UiTypography.CAPTION);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, buildLeftPanel(), buildRightPanel());
        split.setResizeWeight(0.35);
        split.setDividerLocation(360);
        add(split, BorderLayout.CENTER);
        add(errorLabel, BorderLayout.SOUTH);
    }

    @Override public void addNotify() { super.addNotify(); loadBatches(); }

    private JPanel buildLeftPanel() {
        JPanel panel = new JPanel(new BorderLayout(4, 4));
        panel.setOpaque(false);

        // Toolbar
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        toolbar.setOpaque(false);
        JButton createBatch = new JButton("新建批次");
        createBatch.addActionListener(e -> showBatchDialog(null));
        JButton editBatch = new JButton("编辑批次");
        editBatch.addActionListener(e -> { if (batchList.getSelectedValue() != null) showBatchDialog(batchList.getSelectedValue()); });
        JButton addOption = new JButton("新增专业");
        addOption.addActionListener(e -> showOptionDialog());
        JButton refresh = new JButton("刷新");
        refresh.addActionListener(e -> loadBatches());
        toolbar.add(createBatch); toolbar.add(editBatch); toolbar.add(addOption); toolbar.add(refresh);

        // Batch list
        batchListModel = new DefaultListModel<>();
        batchList = new JList<>(batchListModel);
        batchList.setCellRenderer(new BatchCellRenderer());
        batchList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        batchList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) loadApplications();
        });
        JScrollPane batchScroll = new JScrollPane(batchList);
        batchScroll.setBorder(BorderFactory.createTitledBorder("批次列表"));
        batchScroll.setPreferredSize(new Dimension(0, 160));

        // Application list
        appListModel = new DefaultListModel<>();
        appList = new JList<>(appListModel);
        appList.setCellRenderer(new ApplicationCellRenderer());
        appList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        appList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) loadApplicationDetail();
        });
        JScrollPane appScroll = new JScrollPane(appList);
        appScroll.setBorder(BorderFactory.createTitledBorder("申请列表"));
        appScroll.setPreferredSize(new Dimension(0, 300));

        JPanel lists = new JPanel();
        lists.setLayout(new BoxLayout(lists, BoxLayout.Y_AXIS));
        lists.add(batchScroll);
        lists.add(Box.createVerticalStrut(4));
        lists.add(appScroll);

        panel.add(toolbar, BorderLayout.NORTH);
        panel.add(lists, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildRightPanel() {
        detailPanel = new JPanel(new BorderLayout(4, 4));
        detailPanel.setOpaque(false);

        detailHint = new JLabel("选择批次和申请查看详情");
        detailHint.setFont(UiTypography.BODY);
        detailHint.setForeground(UiColors.TEXT_SECONDARY);
        detailHint.setBorder(new EmptyBorder(4, 6, 0, 6));

        // Info area
        infoPanel = new JPanel(new GridBagLayout());
        JScrollPane infoScroll = new JScrollPane(infoPanel);
        infoScroll.setBorder(BorderFactory.createTitledBorder("申请信息"));

        // Review area
        reviewArea = new JTextArea();
        reviewArea.setEditable(false);
        reviewArea.setFont(UiTypography.CAPTION);
        reviewArea.setLineWrap(true);
        reviewArea.setWrapStyleWord(true);
        JScrollPane reviewScroll = new JScrollPane(reviewArea);
        reviewScroll.setBorder(BorderFactory.createTitledBorder("审核记录"));
        reviewScroll.setPreferredSize(new Dimension(0, 120));

        JSplitPane centerSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, infoScroll, reviewScroll);
        centerSplit.setResizeWeight(0.75);

        // Actions
        actionsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        actionsPanel.setOpaque(false);
        actionStatus = new JLabel(" ");
        actionStatus.setFont(UiTypography.CAPTION);

        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.add(detailHint, BorderLayout.NORTH);
        bottomPanel.add(actionsPanel, BorderLayout.CENTER);
        bottomPanel.add(actionStatus, BorderLayout.SOUTH);

        detailPanel.add(centerSplit, BorderLayout.CENTER);
        detailPanel.add(bottomPanel, BorderLayout.SOUTH);
        return detailPanel;
    }

    private void loadBatches() {
        students.listTransferBatches().whenComplete((response, error) ->
                SwingUtilities.invokeLater(() -> {
                    if (response != null && response.success()) {
                        batchListModel.clear();
                        for (var batch : response.data()) batchListModel.addElement(batch);
                    } else {
                        errorLabel.setText(response != null ? response.message() : "加载失败");
                    }
                }));
    }

    private void loadApplications() {
        var batch = batchList.getSelectedValue();
        appListModel.clear();
        if (batch == null) return;
        students.listTransferApplications(new MajorTransferApplicationQuery(batch.batchId(), null, null))
                .whenComplete((response, error) -> SwingUtilities.invokeLater(() -> {
                    if (response != null && response.success()) {
                        for (var app : response.data()) appListModel.addElement(app);
                    } else {
                        errorLabel.setText(response != null ? response.message() : "加载失败");
                    }
                }));
    }

    private void loadApplicationDetail() {
        var app = appList.getSelectedValue();
        if (app == null) {
            detailHint.setText("选择申请查看详情");
            infoPanel.removeAll(); infoPanel.revalidate(); infoPanel.repaint();
            reviewArea.setText("");
            actionsPanel.removeAll(); actionsPanel.revalidate();
            return;
        }
        students.getTransferApplication(app.applicationId()).whenComplete((response, error) ->
                SwingUtilities.invokeLater(() -> {
                    if (response != null && response.success()) renderDetail(response.data());
                    else errorLabel.setText(response == null ? "加载失败" : response.message());
                }));
    }

    private void renderDetail(MajorTransferApplicationView app) {
        infoPanel.removeAll();
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(3, 6, 3, 6); gbc.anchor = GridBagConstraints.WEST;
        int row = 0;
        addInfoRow(infoPanel, gbc, row++, "学生姓名:", app.studentName());
        addInfoRow(infoPanel, gbc, row++, "学号:", app.fromStudentNumber());
        addInfoRow(infoPanel, gbc, row++, "原学院:", app.fromDepartmentName());
        addInfoRow(infoPanel, gbc, row++, "原专业:", app.fromMajorName());
        addInfoRow(infoPanel, gbc, row++, "原班级:", app.fromClassName());
        addInfoRow(infoPanel, gbc, row++, "目标学院:", app.targetDepartmentName());
        addInfoRow(infoPanel, gbc, row++, "目标专业:", app.targetMajorName());
        addInfoRow(infoPanel, gbc, row++, "申请类型:",
                app.applicationType() == MajorTransferApplicationType.ORDINARY ? "普通转专业" : "学困生转专业");
        addInfoRow(infoPanel, gbc, row++, "当前状态:", statusLabel(app.status()));
        for (var attachment : app.attachments()) {
            gbc.gridx = 0; gbc.gridy = row++; gbc.gridwidth = 2;
            JButton dl = new JButton("下载材料：" + attachment.fileName());
            dl.addActionListener(e -> downloadAttachment(attachment));
            infoPanel.add(dl, gbc); gbc.gridwidth = 1;
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
        reasonLabel.setFont(UiTypography.BODY); infoPanel.add(reasonLabel, gbc);
        gbc.gridx = 1; gbc.weightx = 1; gbc.fill = GridBagConstraints.BOTH;
        JTextArea reasonArea = new JTextArea(app.reason() != null ? app.reason() : "(未填写)");
        reasonArea.setEditable(false); reasonArea.setFont(UiTypography.BODY);
        reasonArea.setLineWrap(true); reasonArea.setWrapStyleWord(true); reasonArea.setRows(3);
        infoPanel.add(new JScrollPane(reasonArea), gbc);
        // Filler
        gbc.gridx = 0; gbc.gridy = row + 1; gbc.weighty = 1; gbc.fill = GridBagConstraints.BOTH;
        infoPanel.add(Box.createGlue(), gbc);
        infoPanel.revalidate(); infoPanel.repaint();

        // Reviews
        StringBuilder sb = new StringBuilder();
        if (app.reviews().isEmpty()) sb.append("(暂无审核记录)\n");
        for (var review : app.reviews()) {
            sb.append(reviewStageLabel(review.reviewStage())).append(": ")
                    .append(review.decision() == MajorTransferDecision.APPROVE ? "通过" : "驳回");
            if (review.comment() != null) sb.append(" — ").append(review.comment());
            sb.append("\n");
        }
        reviewArea.setText(sb.toString());

        // Actions
        actionsPanel.removeAll();
        detailHint.setText("当前状态: " + statusLabel(app.status()));
        detailHint.setForeground(ACTION_GREEN);
        actionStatus.setText(" ");

        switch (app.status()) {
            case SUBMITTED -> {
                detailHint.setText("等待原学院审核");
                JButton approve = new JButton("原学院审核通过");
                approve.addActionListener(e -> reviewSource(app, MajorTransferDecision.APPROVE, null));
                JButton reject = new JButton("驳回");
                reject.addActionListener(e -> { String c = promptComment(); if (c != null) reviewSource(app, MajorTransferDecision.REJECT, c); });
                actionsPanel.add(approve); actionsPanel.add(reject);
            }
            case SOURCE_APPROVED -> {
                detailHint.setText("等待转入学院资格审核");
                JButton approve = new JButton("资格审核通过");
                approve.addActionListener(e -> reviewQualification(app, MajorTransferDecision.APPROVE, null));
                JButton reject = new JButton("驳回");
                reject.addActionListener(e -> { String c = promptComment(); if (c != null) reviewQualification(app, MajorTransferDecision.REJECT, c); });
                actionsPanel.add(approve); actionsPanel.add(reject);
            }
            case QUALIFIED -> {
                detailHint.setText("可录入笔试和面试成绩");
                JButton score = new JButton("录入成绩");
                score.addActionListener(e -> showScoreDialog(app));
                actionsPanel.add(score);
            }
            case ASSESSED -> {
                detailHint.setText("可生成拟录取名单");
                JButton proposal = new JButton("生成拟录取名单");
                proposal.addActionListener(e -> generateProposal(app));
                actionsPanel.add(proposal);
            }
            case PROPOSED -> {
                detailHint.setText("可进行终审");
                JButton fin = new JButton("终审通过");
                fin.addActionListener(e -> finalizeApp(app));
                actionsPanel.add(fin);
            }
            case PENDING_EFFECTIVE, EXECUTION_FAILED -> {
                detailHint.setText("请选择目标班级并执行转专业");
                JButton exec = new JButton("执行转专业");
                exec.addActionListener(e -> showExecuteDialog(app));
                actionsPanel.add(exec);
            }
            case DRAFT -> detailHint.setText("草稿状态 — 学生尚未提交");
            case EFFECTIVE -> detailHint.setText("已生效 — 转专业已完成");
            case REJECTED -> { detailHint.setText("已驳回"); detailHint.setForeground(ACTION_RED); }
            case CANCELLED -> detailHint.setText("已取消");
        }
        if (MajorTransferStateMachine.adminMayCancel(app.status())) {
            JButton cancel = new JButton("取消申请");
            cancel.addActionListener(e -> { String r = promptReason(); if (r != null) cancel(app, r); });
            actionsPanel.add(cancel);
        }
        actionsPanel.revalidate(); actionsPanel.repaint();
    }

    private void addInfoRow(JPanel panel, GridBagConstraints gbc, int row, String label, String value) {
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0; gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.WEST;
        JLabel lbl = new JLabel(label); lbl.setFont(UiTypography.BODY); panel.add(lbl, gbc);
        gbc.gridx = 1; gbc.weightx = 1; gbc.fill = GridBagConstraints.HORIZONTAL;
        JLabel val = new JLabel(value != null ? value : ""); val.setFont(UiTypography.BODY);
        if (value != null && !value.isEmpty()) val.setToolTipText(value);
        panel.add(val, gbc);
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
                var cmd = new SaveMajorTransferBatchCommand(batch == null ? null : batch.batchId(),
                        name.getText().trim(), (MajorTransferBatchStatus) status.getSelectedItem(),
                        parseDate(start), parseDate(end), parseDate(publicStart), parseDate(publicEnd),
                        parseDate(effective), batch == null ? 0 : batch.rowVersion());
                students.saveTransferBatch(cmd).whenComplete((response, error) ->
                        SwingUtilities.invokeLater(() -> {
                            if (response != null && response.success()) loadBatches();
                            else errorLabel.setText(response == null ? "保存失败" : response.message());
                        }));
                return;
            } catch (RuntimeException error) {
                JOptionPane.showMessageDialog(this, "请检查日期和字段：" + error.getMessage());
            }
        }
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
                var sel = (edu.seu.vcampus.common.student.DepartmentView) department.getSelectedItem();
                if (sel == null) return;
                students.listMajors(sel.departmentId()).whenComplete((majors, err) -> SwingUtilities.invokeLater(() -> {
                    if (department.getSelectedItem() != sel) return;
                    if (majors != null && majors.success()) majors.data().forEach(major::addItem);
                }));
            });
            if (department.getItemCount() > 0) department.setSelectedIndex(0);
            JTextField grades = new JTextField("2024,2025");
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
            form.add(new JLabel("笔试权重%")); form.add(writtenWeight);
            form.add(new JLabel("笔试合格线")); form.add(writtenPass);
            form.add(new JLabel("面试合格线")); form.add(interviewPass);
            form.add(new JLabel("学困生规则")); form.add(exempt);
            form.add(new JLabel("附加要求")); form.add(requirements);
            while (JOptionPane.showConfirmDialog(this, form, "新增开放专业", JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION) {
                try {
                    var target = (edu.seu.vcampus.common.student.MajorView) major.getSelectedItem();
                    if (target == null) throw new IllegalArgumentException("请选择专业");
                    int w = (Integer) writtenWeight.getValue();
                    var cmd = new SaveMajorTransferOptionCommand(null, batch.batchId(), target.majorId(),
                            grades.getText().trim(), (Integer) quota.getValue(), (Integer) interviews.getValue(),
                            ((Number) writtenPass.getValue()).doubleValue(), ((Number) interviewPass.getValue()).doubleValue(),
                            w, 100 - w, exempt.isSelected(), requirements.getText(), true, 0);
                    students.saveTransferOption(cmd).whenComplete((response, err) -> SwingUtilities.invokeLater(() ->
                            errorLabel.setText(response != null && response.success() ? "开放专业已保存" :
                                    response == null ? "保存失败" : response.message())));
                    return;
                } catch (RuntimeException error) { JOptionPane.showMessageDialog(this, error.getMessage()); }
            }
        }));
    }

    private void showScoreDialog(MajorTransferApplicationView app) {
        JTextField writtenField = new JTextField(10);
        JTextField interviewField = new JTextField(10);
        JPanel panel = new JPanel(new GridLayout(0, 2, 4, 4));
        panel.add(new JLabel("笔试成绩:")); panel.add(writtenField);
        panel.add(new JLabel("面试成绩:")); panel.add(interviewField);
        if (JOptionPane.showConfirmDialog(this, panel, "录入成绩", JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION) {
            try {
                BigDecimal w = writtenField.getText().isBlank() ? null : new BigDecimal(writtenField.getText().trim());
                BigDecimal i = interviewField.getText().isBlank() ? null : new BigDecimal(interviewField.getText().trim());
                var cmd = new RecordMajorTransferScoreCommand(app.applicationId(), w, i, app.applicationVersion());
                students.recordTransferScore(cmd).whenComplete((r, e) -> SwingUtilities.invokeLater(() -> {
                    if (r != null && r.success()) loadApplicationDetail();
                    else errorLabel.setText(r != null ? r.message() : "录入失败");
                }));
            } catch (NumberFormatException e) { JOptionPane.showMessageDialog(this, "成绩请输入数字"); }
        }
    }

    private void showExecuteDialog(MajorTransferApplicationView app) {
        students.listClasses(app.targetMajorId()).whenComplete((response, error) ->
                SwingUtilities.invokeLater(() -> {
                    if (response == null || !response.success()) { errorLabel.setText("加载班级失败"); return; }
                    JComboBox<String> classCombo = new JComboBox<>();
                    for (var cls : response.data()) if (cls.active()) classCombo.addItem(cls.classId() + " - " + cls.name());
                    JPanel panel = new JPanel(new GridLayout(0, 2, 4, 4));
                    panel.add(new JLabel("目标班级:")); panel.add(classCombo);
                    if (JOptionPane.showConfirmDialog(this, panel, "执行转专业", JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION) {
                        String sel = (String) classCombo.getSelectedItem();
                        if (sel == null) return;
                        var cmd = new ExecuteMajorTransferCommand(app.applicationId(), sel.split(" - ")[0], app.applicationVersion());
                        students.executeTransfer(cmd).whenComplete((r, e) -> SwingUtilities.invokeLater(() -> {
                            if (r != null && r.success()) loadApplicationDetail();
                            else errorLabel.setText(r != null ? r.message() : "执行失败");
                        }));
                    }
                }));
    }

    private void downloadAttachment(MajorTransferApplicationView.AttachmentInfo attachment) {
        students.getTransferAttachment(attachment.attachmentId()).whenComplete((response, error) ->
                SwingUtilities.invokeLater(() -> {
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
                    }).whenComplete((ignored, fail) -> SwingUtilities.invokeLater(() ->
                            errorLabel.setText(fail == null ? "材料已保存" : "保存失败")));
                }));
    }

    private void reviewSource(MajorTransferApplicationView app, MajorTransferDecision decision, String comment) {
        JCheckBox academic = new JCheckBox("已核实学籍审核合格");
        JCheckBox conduct = new JCheckBox("已核实无禁止性违纪");
        JCheckBox admission = new JCheckBox("已核实招生类别允许转专业");
        if (decision == MajorTransferDecision.APPROVE) {
            JPanel checks = new JPanel(new GridLayout(0, 1)); checks.add(academic); checks.add(conduct); checks.add(admission);
            if (JOptionPane.showConfirmDialog(this, checks, "原学院资格核实", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
        }
        var cmd = new ReviewMajorTransferSourceCommand(app.applicationId(), decision,
                academic.isSelected(), conduct.isSelected(), admission.isSelected(), comment, app.applicationVersion());
        students.reviewTransferSource(cmd).whenComplete((r, e) -> SwingUtilities.invokeLater(() -> {
            if (r != null && r.success()) { loadApplicationDetail(); actionStatus.setText("审核完成"); }
            else errorLabel.setText(r != null ? r.message() : "审核失败");
        }));
    }

    private void reviewQualification(MajorTransferApplicationView app, MajorTransferDecision decision, String comment) {
        var cmd = new ReviewMajorTransferQualificationCommand(app.applicationId(), decision, comment, app.applicationVersion());
        students.reviewTransferQualification(cmd).whenComplete((r, e) -> SwingUtilities.invokeLater(() -> {
            if (r != null && r.success()) { loadApplicationDetail(); actionStatus.setText("审核完成"); }
            else errorLabel.setText(r != null ? r.message() : "审核失败");
        }));
    }

    private void generateProposal(MajorTransferApplicationView app) {
        students.listTransferOptions(app.batchId()).whenComplete((r, e) -> SwingUtilities.invokeLater(() -> {
            if (r == null || !r.success()) { errorLabel.setText("专业配置加载失败"); return; }
            var option = r.data().stream().filter(o -> o.optionId().equals(app.optionId())).findFirst().orElseThrow();
            if (JOptionPane.showConfirmDialog(this, "确认生成拟录取名单？", "生成拟录取", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
            students.generateTransferProposal(new GenerateMajorTransferProposalCommand(option.optionId(), option.rowVersion()))
                    .whenComplete((result, fail) -> SwingUtilities.invokeLater(() -> {
                        if (result == null || !result.success()) { errorLabel.setText(result == null ? "生成失败" : result.message()); return; }
                        StringBuilder text = new StringBuilder();
                        result.data().applicants().forEach(a -> text.append(a.studentName()).append(" ")
                                .append(a.finalScore()).append(a.proposed() ? "  拟录取" : "  未录取").append("\n"));
                        JOptionPane.showMessageDialog(this, new JScrollPane(new JTextArea(text.toString(), 14, 40)), "拟录取结果", JOptionPane.INFORMATION_MESSAGE);
                        loadApplicationDetail();
                    }));
        }));
    }

    private void finalizeApp(MajorTransferApplicationView app) {
        var cmd = new FinalizeMajorTransferCommand(app.applicationId(), app.applicationVersion());
        students.finalizeTransfer(cmd).whenComplete((r, e) -> SwingUtilities.invokeLater(() -> {
            if (r != null && r.success()) { loadApplicationDetail(); actionStatus.setText("终审通过"); }
            else errorLabel.setText(r != null ? r.message() : "终审失败");
        }));
    }

    private void cancel(MajorTransferApplicationView app, String reason) {
        var cmd = new CancelMajorTransferCommand(app.applicationId(), reason, app.applicationVersion());
        students.cancelTransfer(cmd).whenComplete((r, e) -> SwingUtilities.invokeLater(() -> {
            if (r != null && r.success()) loadApplicationDetail();
            else errorLabel.setText(r != null ? r.message() : "取消失败");
        }));
    }

    private String promptComment() {
        String c = JOptionPane.showInputDialog(this, "请输入原因:");
        return (c != null && !c.isBlank()) ? c.trim() : null;
    }

    private String promptReason() {
        String r = JOptionPane.showInputDialog(this, "取消原因:");
        return (r != null && !r.isBlank()) ? r.trim() : null;
    }

    private static JTextField dateField(Instant value) {
        var fmt = java.time.format.DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm");
        JTextField f = new JTextField(value == null ? "" : fmt.format(value.atZone(ZoneId.of("Asia/Shanghai"))), 20);
        f.setToolTipText("北京时间，格式：2026-09-10 09:00");
        return f;
    }

    private static Instant parseDate(JTextField field) {
        if (field.getText().isBlank()) return null;
        return java.time.LocalDateTime.parse(field.getText().trim(),
                java.time.format.DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm")
                        .withResolverStyle(java.time.format.ResolverStyle.STRICT))
                .atZone(ZoneId.of("Asia/Shanghai")).toInstant();
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

    private static class BatchCellRenderer extends DefaultListCellRenderer {
        @Override public Component getListCellRendererComponent(JList<?> list, Object value,
                int index, boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof MajorTransferBatchView b) setText(b.batchName() + " [" + b.status() + "]");
            return this;
        }
    }

    private static class ApplicationCellRenderer extends DefaultListCellRenderer {
        @Override public Component getListCellRendererComponent(JList<?> list, Object value,
                int index, boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof MajorTransferApplicationView app) {
                String score = app.finalScore() != null ? " 总分:" + app.finalScore() : "";
                String s = switch (app.status()) {
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
                setText(s + " " + app.studentName() + " " + app.fromMajorName() + " → " + app.targetMajorName() + score);
                if (!isSelected) setForeground(switch (app.status()) {
                    case DRAFT -> Color.GRAY;
                    case SUBMITTED -> new Color(33, 150, 243);
                    case SOURCE_APPROVED, QUALIFIED -> new Color(255, 152, 0);
                    case PROPOSED, PENDING_EFFECTIVE -> new Color(76, 175, 80);
                    case EFFECTIVE -> new Color(139, 195, 74);
                    case REJECTED, CANCELLED, EXECUTION_FAILED -> new Color(244, 67, 54);
                    case ASSESSED -> Color.BLACK;
                });
            }
            return this;
        }
    }
}
