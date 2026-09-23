package ai.timefold.solver.core.preview.api.move.builtin;

import java.util.List;
import java.util.Objects;

import ai.timefold.solver.core.impl.domain.solution.descriptor.DefaultPlanningVariableMetaModel;
import ai.timefold.solver.core.impl.move.builtin.MoveProviderUtil;
import ai.timefold.solver.core.impl.move.builtin.ViolationScopedMoveSupport;
import ai.timefold.solver.core.preview.api.domain.metamodel.GenuineEntityMetaModel;
import ai.timefold.solver.core.preview.api.domain.metamodel.PlanningVariableMetaModel;
import ai.timefold.solver.core.preview.api.move.Move;
import ai.timefold.solver.core.preview.api.move.SolutionView;
import ai.timefold.solver.core.preview.api.neighborhood.BiMoveConstructor;
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
 * The swap counterpart of {@link ViolationScopedChangeMoveProvider}.
 * Unlike {@link SwapMoveProvider}, which considers every pair of entities,
 * this provider only considers pairs of which at least one side
 * is currently justified by a hard-negative constraint match,
 * resolved from the score director behind
 * the {@link ai.timefold.solver.core.preview.api.move.SolutionView}.
 * Pair legality is the same as {@link SwapMoveProvider}:
 * at least one listed variable differs and every differing variable is legal on both entities.
 *
 * <p>
 * RATIONALE FOR THE PAIR SCOPE. A violation is rarely cured by mutating its own entities alone:
 * the cure often arrives from an innocent entity that takes the wounded entity's place,
 * which is why both sides of the pair are accepted here.
 * The wound supplies the direction, the partner supplies the movement.
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
 * <li>The match set is recomputed on every joiner evaluation, with no caching between steps;
 * a real implementation would cache it per step and refresh it on solution changes.</li>
 * <li>There is no partner restriction yet (for example, same-group partners only);
 * the full pair grammar is considered. Scoping the partner side is left to the API discussion.</li>
 * </ul>
 *
 * @param <Solution_> the solution type, the class with the {@code @PlanningSolution} annotation
 * @param <Entity_> the entity type
 * @see SwapMoveProvider Swapping entity pairs, unscoped.
 * @see ViolationScopedChangeMoveProvider Changing a single violation target at a time.
 */
@NullMarked
public final class ViolationScopedSwapMoveProvider<Solution_, Entity_>
        implements MoveProvider<Solution_> {

    private final GenuineEntityMetaModel<Solution_, Entity_> entityMetaModel;
    private final List<PlanningVariableMetaModel<Solution_, Entity_, Object>> variableMetaModelList;

    /**
     * As defined by {@link #ViolationScopedSwapMoveProvider(List)},
     * but for every basic planning variable of {@code entityMetaModel}.
     */
    public ViolationScopedSwapMoveProvider(GenuineEntityMetaModel<Solution_, Entity_> entityMetaModel) {
        this(MoveProviderUtil.basicVariablesOf(entityMetaModel));
    }

    /**
     * As defined by {@link #ViolationScopedSwapMoveProvider(List)}, but for a single variable.
     */
    public ViolationScopedSwapMoveProvider(PlanningVariableMetaModel<Solution_, Entity_, ?> variableMetaModel) {
        this(List.of(variableMetaModel));
    }

    /**
     * A pair is proposed only when at least one side is a violation target,
     * when at least one listed variable differs
     * and when every differing variable is legal on both entities;
     * if any differing variable is out of range, the pair is skipped entirely.
     * All variables must belong to the same entity class.
     *
     * @param variableMetaModelList must not be empty
     */
    public ViolationScopedSwapMoveProvider(
            List<? extends PlanningVariableMetaModel<Solution_, Entity_, ?>> variableMetaModelList) {
        this.variableMetaModelList = MoveProviderUtil.normalize(variableMetaModelList);
        this.entityMetaModel = this.variableMetaModelList.getFirst().entity();
    }

    @Override
    public MoveStream<Solution_> build(MoveStreamFactory<Solution_> moveStreamFactory) {
        var entityType = entityMetaModel.type();
        var entityStream = moveStreamFactory.forEach(entityType, false);
        var moveConstructor = (BiMoveConstructor<Solution_, Entity_, Entity_>) this::buildMove;
        // We do not exclude duplicate swaps (A<>B and B<>A), just like SwapMoveProvider,
        // to keep it simple and fast.
        return moveStreamFactory.pick(entityStream)
                .pick(entityStream,
                        NeighborhoodsJoiners.filtering(this::touchesViolationTarget),
                        NeighborhoodsJoiners.filtering(this::isValidSwap))
                .asMove(moveConstructor);
    }

    private Move<Solution_> buildMove(SolutionView<Solution_> solutionView, Entity_ a, Entity_ b) {
        return Moves.swap(variableMetaModelList, a, b);
    }

    private boolean touchesViolationTarget(SolutionView<Solution_> solutionView, Entity_ leftEntity,
            Entity_ rightEntity) {
        var targets = ViolationScopedMoveSupport.findHardNegativeMatchFacts(solutionView);
        return targets.contains(leftEntity) || targets.contains(rightEntity);
    }

    private boolean isValidSwap(SolutionView<Solution_> solutionView, Entity_ leftEntity, Entity_ rightEntity) {
        if (leftEntity == rightEntity) {
            return false;
        }
        var change = false;
        for (var variableMetaModel : variableMetaModelList) {
            var defaultVariableMetaModel = (DefaultPlanningVariableMetaModel<Solution_, Entity_, Object>) variableMetaModel;
            var variableDescriptor = defaultVariableMetaModel.variableDescriptor();
            var oldLeftValue = variableDescriptor.getValue(leftEntity);
            var oldRightValue = variableDescriptor.getValue(rightEntity);
            if (Objects.equals(oldLeftValue, oldRightValue)) {
                continue;
            }
            if (solutionView.isValueInRange(variableMetaModel, leftEntity, oldRightValue)
                    && solutionView.isValueInRange(variableMetaModel, rightEntity, oldLeftValue)) {
                change = true;
            } else {
                // One of the swaps falls out of range, skip this pair altogether.
                return false;
            }
        }
        return change;
    }

}
