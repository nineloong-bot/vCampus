package edu.seu.vcampus.client.student.ui;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class StudentProfileStatusViewTest {
    @Test
    void successfulProfileWithoutApplicationOmitsRedundantStatusText() {
        StudentProfileStatusView statuses = new StudentProfileStatusView();

        statuses.loaded();
        statuses.showApplication(null);

        assertThat(statuses.loadingLabel().getText()).isEmpty();
        assertThat(statuses.applicationLabel().getText()).isEmpty();
    }
}
