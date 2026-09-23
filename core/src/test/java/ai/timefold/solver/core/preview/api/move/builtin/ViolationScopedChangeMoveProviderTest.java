package ai.timefold.solver.core.preview.api.move.builtin;

import ai.timefold.solver.core.api.score.SimpleScore;
import ai.timefold.solver.core.api.score.stream.Constraint;
import ai.timefold.solver.core.api.score.stream.ConstraintFactory;
import ai.timefold.solver.core.api.score.stream.ConstraintProvider;
import ai.timefold.solver.core.impl.score.constraint.ConstraintMatchPolicy;
import ai.timefold.solver.core.preview.api.domain.metamodel.PlanningVariableMetaModel;
import ai.timefold.solver.core.preview.api.move.test.MoveTester;
import ai.timefold.solver.core.preview.api.neighborhood.test.NeighborhoodTester;
import ai.timefold.solver.core.testdomain.TestdataEntity;
import ai.timefold.solver.core.testdomain.TestdataSolution;
import ai.timefold.solver.core.testdomain.TestdataValue;

import org.assertj.core.api.Assertions;
import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.Test;

/**
 * Demonstrates the claim of the violation-scoped PoC: with the constraint match policy
 * ENABLED (via the {@link MoveTester} knob), only the entities justified by a hard-negative
 * constraint match generate moves.
 */
@NullMarked
class ViolationScopedChangeMoveProviderTest {

    @Test
    void changeMovesOnlyAroundHardNegativeMatchTargets() {
        var solutionMetaModel = TestdataSolution.buildMetaModel();
        PlanningVariableMetaModel<TestdataSolution, TestdataEntity, TestdataValue> variableMetaModel =
                solutionMetaModel.genuineEntity(TestdataEntity.class)
                        .basicVariable("value", TestdataValue.class);

        var solution = TestdataSolution.generateSolution(2, 2);
        var e1 = solution.getEntityList().get(0);
        var e2 = solution.getEntityList().get(1);
        var woundedValue = solution.getValueList().get(0);
        var healthyValue = solution.getValueList().get(1);
        e1.setValue(woundedValue);
        e2.setValue(healthyValue);

        var constraintProvider = new SingleWoundedValueConstraintProvider(woundedValue);
        var context = NeighborhoodTester.build(new ViolationScopedChangeMoveProvider<>(variableMetaModel),
                solutionMetaModel,
                tester -> MoveTester.build(solutionMetaModel, constraintProvider)
                        .withConstraintMatchPolicy(ConstraintMatchPolicy.ENABLED))
                .using(solution);

        // e1 carries the hard-negative match: its change moves are generated.
        context.producesAllOf(Moves.change(variableMetaModel, e1, healthyValue));
        // e2 carries no match: none of its change moves are generated,
        // and e1 never changes to its own current value.
        context.producesNoneOf(
                Moves.change(variableMetaModel, e2, woundedValue),
                Moves.change(variableMetaModel, e2, healthyValue),
                Moves.change(variableMetaModel, e1, woundedValue));
    }

    @Test
    void failsLoudWhenConstraintMatchPolicyDisabled() {
        var solutionMetaModel = TestdataSolution.buildMetaModel();
        PlanningVariableMetaModel<TestdataSolution, TestdataEntity, TestdataValue> variableMetaModel =
                solutionMetaModel.genuineEntity(TestdataEntity.class)
                        .basicVariable("value", TestdataValue.class);
        var solution = TestdataSolution.generateSolution(2, 2);

        var tester = NeighborhoodTester.build(new ViolationScopedChangeMoveProvider<>(variableMetaModel),
                solutionMetaModel); // Default policy DISABLED.
        // The fail-loud fires while binding the solution, when the move repository initializes.
        Assertions.assertThatIllegalStateException()
                .isThrownBy(() -> tester.using(solution));
    }

    /**
     * Penalizes exactly the entities holding {@code woundedValue} with a hard-negative match,
     * leaving every other entity clean.
     */
    public static final class SingleWoundedValueConstraintProvider implements ConstraintProvider {

        private final TestdataValue woundedValue;

        public SingleWoundedValueConstraintProvider(TestdataValue woundedValue) {
            this.woundedValue = woundedValue;
        }

        @Override
        public Constraint @org.jspecify.annotations.NonNull [] defineConstraints(
                @org.jspecify.annotations.NonNull ConstraintFactory constraintFactory) {
            return new Constraint[] { woundedEntityConstraint(constraintFactory) };
        }

        private Constraint woundedEntityConstraint(ConstraintFactory constraintFactory) {
            return constraintFactory.forEach(TestdataEntity.class)
                    .filter(entity -> entity.getValue() == woundedValue)
                    .penalize(SimpleScore.ONE)
                    .asConstraint("Wounded entity");
        }

    }

}
