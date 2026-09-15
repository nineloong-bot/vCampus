package edu.seu.vcampus.client.student.majortransfer.ui;

import edu.seu.vcampus.common.student.majortransfer.MajorTransferOptionView;

/** Combo-box item wrapping a selectable major-transfer option. */
record MajorTransferOptionItem(MajorTransferOptionView option) {
    @Override public String toString() {
        return option.targetDepartmentName() + " / " + option.targetMajorName()
                + " (名额:" + option.receiveQuota() + ")";
    }
}
