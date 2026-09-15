package edu.seu.vcampus.client.course.ui;

import edu.seu.vcampus.client.core.ui.autocomplete.AutocompleteChoice;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OfferingAutocompleteTest {
    @Test
    void courseAndTeacherQueriesReturnStableIdsAndAtMostEightChoices() {
        OfferingReferenceLoader loader = new OfferingReferenceLoader(CourseUiGateway.preview());

        assertThat(loader.searchCourses("程序", 8).join()).hasSizeLessThanOrEqualTo(8)
                .allSatisfy(choice -> assertThat(choice.id()).isNotBlank());
        assertThat(loader.searchTeachers("teacher", 8).join()).hasSizeLessThanOrEqualTo(8)
                .extracting(AutocompleteChoice::id).doesNotContainNull();
    }
}
