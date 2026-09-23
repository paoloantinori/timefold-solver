package ai.timefold.solver.core.impl.move;

import java.util.Objects;

import ai.timefold.solver.core.api.score.Score;
import ai.timefold.solver.core.api.score.stream.ConstraintProvider;
import ai.timefold.solver.core.config.solver.EnvironmentMode;
import ai.timefold.solver.core.impl.domain.solution.descriptor.DefaultPlanningSolutionMetaModel;
import ai.timefold.solver.core.impl.score.constraint.ConstraintMatchPolicy;
import ai.timefold.solver.core.impl.score.director.AbstractScoreDirectorFactory;
import ai.timefold.solver.core.impl.score.director.stream.BavetConstraintStreamScoreDirectorFactory;
import ai.timefold.solver.core.preview.api.domain.metamodel.PlanningSolutionMetaModel;
import ai.timefold.solver.core.preview.api.move.test.MoveTestContext;
import ai.timefold.solver.core.preview.api.move.test.MoveTester;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class DefaultMoveTester<Solution_> implements MoveTester<Solution_> {

    private final AbstractScoreDirectorFactory<Solution_, ?, ?> scoreDirectorFactory;
    private ConstraintMatchPolicy constraintMatchPolicy = ConstraintMatchPolicy.DISABLED;

    public DefaultMoveTester(PlanningSolutionMetaModel<Solution_> solutionMetaModel) {
        this(solutionMetaModel, null);
    }

    public DefaultMoveTester(PlanningSolutionMetaModel<Solution_> solutionMetaModel,
            @Nullable ConstraintProvider constraintProvider) {
        this(buildScoreDirectorFactory(solutionMetaModel, constraintProvider));
    }

    private static <Solution_, Score_ extends Score<Score_>> AbstractScoreDirectorFactory<Solution_, Score_, ?>
            buildScoreDirectorFactory(PlanningSolutionMetaModel<Solution_> solutionMetaModel,
                    @Nullable ConstraintProvider constraintProvider) {
        var solutionDescriptor = ((DefaultPlanningSolutionMetaModel<Solution_>) Objects
                .requireNonNull(solutionMetaModel)).solutionDescriptor();
        return constraintProvider == null
                ? new MoveTesterScoreDirectorFactory<>(solutionDescriptor, EnvironmentMode.FULL_ASSERT)
                : new BavetConstraintStreamScoreDirectorFactory<>(solutionDescriptor, constraintProvider,
                        EnvironmentMode.FULL_ASSERT);
    }

    private DefaultMoveTester(AbstractScoreDirectorFactory<Solution_, ?, ?> scoreDirectorFactory) {
        this.scoreDirectorFactory = Objects.requireNonNull(scoreDirectorFactory, "scoreDirectorFactory");
    }

    @Override
    public DefaultMoveTester<Solution_> withConstraintMatchPolicy(ConstraintMatchPolicy constraintMatchPolicy) {
        this.constraintMatchPolicy = Objects.requireNonNull(constraintMatchPolicy, "constraintMatchPolicy");
        return this;
    }

    @Override
    public MoveTestContext<Solution_> using(Solution_ solution) {
        // Create a score director from the cached factory
        var scoreDirector = scoreDirectorFactory.createScoreDirectorBuilder()
                .withLookUpEnabled(false)
                .withConstraintMatchPolicy(constraintMatchPolicy)
                .build();
        // Set the working solution, which triggers shadow variable initialization
        scoreDirector.setWorkingSolution(Objects.requireNonNull(solution, "solution"));

        return new DefaultMoveTestContext<>(scoreDirector);
    }

}
