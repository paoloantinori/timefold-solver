package ai.timefold.solver.core.preview.api.move.builtin;

import java.util.Objects;

import ai.timefold.solver.core.impl.move.builtin.ViolationScopedMoveSupport;
import ai.timefold.solver.core.impl.neighborhood.stream.DefaultMoveStreamFactory;
import ai.timefold.solver.core.preview.api.domain.metamodel.PlanningVariableMetaModel;
import ai.timefold.solver.core.preview.api.neighborhood.MoveProvider;
import ai.timefold.solver.core.preview.api.neighborhood.stream.MoveStream;
import ai.timefold.solver.core.preview.api.neighborhood.stream.MoveStreamFactory;
import ai.timefold.solver.core.preview.api.neighborhood.stream.joiner.NeighborhoodsJoiners;

import org.jspecify.annotations.NullMarked;

/**
 * Proof of concept for violation-directed move selection,
 * answering the invitation in <a href="https://github.com/TimefoldAI/timefold-solver/issues/891">issue #891</a>
 * (Guided Local Search; the Indictment API as its building block).
 *
 * <p>
 * Unlike {@link ChangeMoveProvider}, which considers every unpinned entity,
 * this provider only considers entities that are currently justified by
 * a hard-negative constraint match, resolved from the score director behind
 * the {@link ai.timefold.solver.core.preview.api.move.SolutionView}.
 * For each such entity, it creates the same change moves as {@link ChangeMoveProvider},
 * preserving value legality (different value, value in range).
 *
 * <p>
 * MOTIVATION. Move selectors enumerate static entity/value lists for performance;
 * violation-directed selection costs a constraint-match lookup per proposal instead.
 * On a small hard residual at the end of solving, that trade is worth making:
 * a production finisher built on this exact selection shape
 * (score with constraint matches enabled, targets = entities of hard-negative matches,
 * change/swap only around targets, steepest descent with incremental move evaluation
 * at roughly 0.05 ms per evaluation) closes hard residuals that plain local search leaves open,
 * because over the full move grammar no single improving move exists until the search
 * starts from the wound itself.
 *
 * <p>
 * REQUIREMENTS. The score director must run with constraint matches
 * and justifications enabled ({@code ConstraintMatchPolicy.ENABLED}); move selection fails
 * fast with {@link IllegalStateException} otherwise. Only fact-bearing justifications
 * (the default) contribute targets; constraints with a custom justification mapping are skipped.
 *
 * <p>
 * KNOWN LIMITS OF THIS PROOF OF CONCEPT.
 * <ul>
 * <li>The match set is recomputed on every filter evaluation, with no caching between steps;
 * a real implementation would cache it per step and refresh it on solution changes.</li>
 * <li>Pattern context is not covered: the cure of an A-B-A pattern may live on the middle entity,
 * which is not a fact of the match. Supporting that generically requires an explicit
 * justification-to-targets mapping hook; this PoC does not define one.</li>
 * </ul>
 *
 * @param <Solution_> the solution type, the class with the {@code @PlanningSolution} annotation
 * @param <Entity_> the entity type
 * @param <Value_> the value type
 * @see ChangeMoveProvider Changing a single entity at a time, unscoped.
 * @see ViolationScopedSwapMoveProvider Swapping pairs that touch a violation target.
 */
@NullMarked
public final class ViolationScopedChangeMoveProvider<Solution_, Entity_, Value_>
        implements MoveProvider<Solution_> {

    private final PlanningVariableMetaModel<Solution_, Entity_, Value_> variableMetaModel;
    private final boolean crossingNull;

    public ViolationScopedChangeMoveProvider(PlanningVariableMetaModel<Solution_, Entity_, Value_> variableMetaModel) {
        this(variableMetaModel, variableMetaModel.allowsUnassigned());
    }

    /**
     * @param crossingNull if {@code true}, also creates assign and unassign moves;
     *        requires that the variable {@link PlanningVariableMetaModel#allowsUnassigned() allows unassigned},
     *        otherwise the constructor throws {@link IllegalArgumentException}
     */
    public ViolationScopedChangeMoveProvider(PlanningVariableMetaModel<Solution_, Entity_, Value_> variableMetaModel,
            boolean crossingNull) {
        this.variableMetaModel = Objects.requireNonNull(variableMetaModel);
        if (crossingNull && !variableMetaModel.allowsUnassigned()) {
            throw new IllegalArgumentException("""
                    The crossingNull (true) of variableMetaModel (%s) requires a variable \
                    which allows unassigned values, but this variable does not.
                    Maybe set crossingNull to false."""
                    .formatted(variableMetaModel));
        }
        this.crossingNull = crossingNull;
    }

    @Override
    public MoveStream<Solution_> build(MoveStreamFactory<Solution_> moveStreamFactory) {
        var nodeSharingSupportFunctions =
                ((DefaultMoveStreamFactory<Solution_>) moveStreamFactory).getNodeSharingSupportFunctions(variableMetaModel);
        var entities = moveStreamFactory.forEach(variableMetaModel.entity().type(), false);
        if (!crossingNull && variableMetaModel.allowsUnassigned()) {
            entities = entities.filter(nodeSharingSupportFunctions.assignedValueFilter());
        }
        // The scoping filter: only entities that some hard-negative match justifies survive.
        // It reads the current constraint matches through the solution view, so the selection
        // follows the working solution instead of a static entity list.
        var violationTargets = entities.filter(
                (solutionView, entity) -> ViolationScopedMoveSupport.findHardNegativeMatchFacts(solutionView)
                        .contains(entity));
        return moveStreamFactory.pick(violationTargets)
                .pick(moveStreamFactory.forEach(variableMetaModel.type(), crossingNull),
                        NeighborhoodsJoiners.filtering(nodeSharingSupportFunctions.differentValueFilter()),
                        NeighborhoodsJoiners.filtering(nodeSharingSupportFunctions.valueInRangeFilter()))
                .asMove((solution, entity, value) -> Moves.change(variableMetaModel, Objects.requireNonNull(entity), value));
    }

}
