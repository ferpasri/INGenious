package com.ing.ide.main.testar.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import org.testng.annotations.Test;

public class BddStepTrackerTest {
    private static final String FIRST_STEP = "When the user selects an option";
    private static final String SECOND_STEP = "Then the result is shown";

    @Test
    public void allowsMultipleOperationsForTheCurrentBddStep() {
        BddStepTracker tracker = new BddStepTracker(
            "Scenario: Example\n" + FIRST_STEP + "\n" + SECOND_STEP
        );

        tracker.saveExecutedBddStep(FIRST_STEP);

        assertThat(tracker.validateBddStep(FIRST_STEP)).isNull();
        assertThat(tracker.validateBddStep(FIRST_STEP)).isNull();
    }

    @Test
    public void rejectsAnOldBddStepAfterMovingToAnotherStep() {
        BddStepTracker tracker = new BddStepTracker(
            "Scenario: Example\n" + FIRST_STEP + "\n" + SECOND_STEP
        );

        tracker.saveExecutedBddStep(FIRST_STEP);
        tracker.saveExecutedBddStep(SECOND_STEP);

        Feedback feedback = tracker.validateBddStep(FIRST_STEP);

        assertThat(feedback).isNotNull();
        assertThat(feedback.getCode()).isEqualTo(Feedback.Code.BDD_STEP_STALE_REUSE);
    }

    @Test
    public void normalizesSmartQuotesForValidationAndTracking() {
        BddStepTracker tracker = new BddStepTracker(
            "Scenario: Example\nWhen the user selects 'an option'"
        );

        String smartQuoteStep = "When the user selects \u2018an option\u2019";

        assertThat(tracker.validateBddStep(smartQuoteStep)).isNull();
        tracker.saveExecutedBddStep(smartQuoteStep);
        assertThat(tracker.getExecutedBddSteps())
            .containsExactly("When the user selects 'an option'");
    }
}
