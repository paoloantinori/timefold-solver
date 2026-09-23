package ai.timefold.solver.core.preview.api.neighborhood.test;

import java.util.function.UnaryOperator;

import ai.timefold.solver.core.impl.neighborhood.DefaultNeighborhoodTester;
import ai.timefold.solver.core.preview.api.domain.metamodel.PlanningSolutionMetaModel;
import ai.timefold.solver.core.preview.api.move.test.MoveTester;
import ai.timefold.solver.core.preview.api.neighborhood.MoveProvider;

import org.jspecify.annotations.NullMarked;

/**
 * Entry point for evaluating {@link MoveProvider}s on a given solution.
 * Given a planning solution, it produces an {@link NeighborhoodTestContext}
 * which contains all moves that can be generated from that solution
 * using the provided {@link MoveProvider}.
 * <p>
 * Example usage:
 *
 * <pre>{@code
 * var solutionMetaModel = PlanningSolutionMetaModel.of(MySolution.class, MyEntity.class);
 * var context = NeighborhoodTester.build(new MyMoveProvider(), solutionMetaModel)
 *         .using(solution);
 * var moveIterator = context.getMovesAsIterator();
 *
 * while (moveIterator.hasNext()) {
 *     var move = moveIterator.next();
 *     // Run assertions on the move here.
 * }
 * }</pre>
 *
 * @param <Solution_> the planning solution type
 */
@NullMarked
public interface NeighborhoodTester<Solution_> {

    /**
     * Creates a new {@link NeighborhoodTester} for the given move provider
     * and the given solution and entity classes.
     * <p>
     * This method validates inputs, and initializes many internal structures.
     * These are heavy operations performed once and cached for reuse.
     * <p>
     * Shadow variables are initialized later when a solution is bound via {@link #using(Object)}.
     *
     * @param moveProvider the move provider to generate moves
     * @param solutionMetaModel the planning solution meta-model;
     *        use {@link PlanningSolutionMetaModel#of(Class, Class[])} to build one.
     * @param <Solution_> the planning solution type
     * @return a new {@link NeighborhoodTester} instance
     */
    static <Solution_> NeighborhoodTester<Solution_> build(MoveProvider<Solution_> moveProvider,
            PlanningSolutionMetaModel<Solution_> solutionMetaModel) {
        return new DefaultNeighborhoodTester<>(moveProvider, solutionMetaModel);
    }

    /**
     * Like {@link #build(MoveProvider, PlanningSolutionMetaModel)}, but allows customizing the
     * underlying {@link MoveTester}, for example to score solutions with a real
     * {@link ai.timefold.solver.core.api.score.stream.ConstraintProvider} and enable
     * constraint matches, which constraint-match-based move providers need.
     *
     * @param moveProvider the move provider to generate moves
     * @param solutionMetaModel the planning solution meta-model;
     *        use {@link PlanningSolutionMetaModel#of(Class, Class[])} to build one.
     * @param moveTesterConfigurator customizes the underlying {@link MoveTester}
     * @param <Solution_> the planning solution type
     * @return a new instance
     */
    static <Solution_> NeighborhoodTester<Solution_> build(MoveProvider<Solution_> moveProvider,
            PlanningSolutionMetaModel<Solution_> solutionMetaModel,
            UnaryOperator<MoveTester<Solution_>> moveTesterConfigurator) {
        return new DefaultNeighborhoodTester<>(moveProvider, solutionMetaModel, moveTesterConfigurator);
    }

    /**
     * Creates an evaluation context for the given solution instance.
     * Once you have the context,
     * you can retrieve the moves via methods such as {@link NeighborhoodTestContext#getMovesAsStream()}.
     * <p>
     * Different evaluation contexts can be created, each bound to a different solution instance.
     * They will operate independently of each other.
     *
     * @param solution the planning solution instance
     * @return a new execution context bound to the given solution
     */
    NeighborhoodTestContext<Solution_> using(Solution_ solution);

}
