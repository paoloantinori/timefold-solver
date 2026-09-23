package ai.timefold.solver.core.impl.move.builtin;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

import ai.timefold.solver.core.api.score.BendableBigDecimalScore;
import ai.timefold.solver.core.api.score.BendableScore;
import ai.timefold.solver.core.api.score.HardMediumSoftBigDecimalScore;
import ai.timefold.solver.core.api.score.HardMediumSoftScore;
import ai.timefold.solver.core.api.score.HardSoftBigDecimalScore;
import ai.timefold.solver.core.api.score.HardSoftScore;
import ai.timefold.solver.core.api.score.Score;
import ai.timefold.solver.core.api.score.SimpleBigDecimalScore;
import ai.timefold.solver.core.api.score.SimpleScore;
import ai.timefold.solver.core.api.score.stream.DefaultConstraintJustification;
import ai.timefold.solver.core.impl.move.InnerMutableSolutionView;
import ai.timefold.solver.core.impl.score.constraint.ConstraintMatchPolicy;
import ai.timefold.solver.core.impl.score.director.InnerScoreDirector;
import ai.timefold.solver.core.preview.api.move.SolutionView;

import org.jspecify.annotations.NullMarked;

/**
 * Shared machinery of the violation-scoped move providers (PoC for guided local search,
 * see https://github.com/TimefoldAI/timefold-solver/issues/891).
 *
 * <p>
 * Resolves the set of objects that some currently hard-negative constraint match justifies,
 * directly from the score director behind the {@link SolutionView}.
 * This is the successor of the Indictment API role: the Indictment API answered
 * "which entities cause my score penalties", and this answers the same question
 * for the move selectors that want to operate only on the wounded entities.
 *
 * <p>
 * Internal: the public discussion of what this API surface should be
 * (public hook on the Neighborhoods API vs. enterprise score analysis) is still open;
 * until then, only the violation-scoped PoC providers in
 * {@code ai.timefold.solver.core.preview.api.move.builtin} may call this.
 */
@NullMarked
public final class ViolationScopedMoveSupport {

    private ViolationScopedMoveSupport() {
    }

    /**
     * Finds every object justified by a hard-negative constraint match of the current working solution.
     *
     * <p>
     * Only fact-bearing justifications (the default {@link DefaultConstraintJustification}) contribute.
     * Constraints with a custom justification mapping are skipped: their justifications carry no facts,
     * so there is nothing generic to target; mapping those to entities is the open design question
     * this PoC deliberately leaves to the API discussion.
     *
     * @param solutionView never null; must be the solver's internal view of the working solution
     * @return never null; identity-semantics set, possibly empty when the solution has no hard-negative matches
     * @throws IllegalStateException when the score director does not run with
     *         {@link ConstraintMatchPolicy#ENABLED}, or when the view does not reach a score director
     */
    public static <Solution_> Set<Object> findHardNegativeMatchFacts(SolutionView<Solution_> solutionView) {
        if (!(solutionView instanceof InnerMutableSolutionView<Solution_> innerView)) {
            throw new IllegalStateException("""
                    The violation-scoped move providers require the solver's internal solution view, \
                    but got %s instead.
                    This is a bug in the move selection wiring."""
                    .formatted(solutionView.getClass().getName()));
        }
        var scoreDirector = (InnerScoreDirector<Solution_, ?>) innerView.getScoreDirector();
        if (scoreDirector.getConstraintMatchPolicy() != ConstraintMatchPolicy.ENABLED) {
            throw new IllegalStateException("""
                    The violation-scoped move providers require constraint matches with justifications, \
                    but the constraint match policy is %s.
                    Enable %s on the score director to use violation-directed move selection."""
                    .formatted(scoreDirector.getConstraintMatchPolicy(), ConstraintMatchPolicy.ENABLED));
        }
        // The match data is only as fresh as the last score calculation; move selection runs on a
        // freshly scored working solution at each step boundary, which is what this PoC assumes.
        var targets = Collections.<Object> newSetFromMap(new IdentityHashMap<>());
        for (var constraintMatchTotal : scoreDirector.getConstraintMatchTotalMap().values()) {
            for (var match : constraintMatchTotal.getConstraintMatchSet()) {
                if (!hasNegativeHardLevel(match.getScore())) {
                    continue;
                }
                var justification = match.getJustification();
                if (justification instanceof DefaultConstraintJustification defaultJustification) {
                    for (var fact : defaultJustification.getFacts()) {
                        if (fact != null) {
                            targets.add(fact);
                        }
                    }
                }
            }
        }
        return targets;
    }

    /**
     * Checks whether any hard level of the score is negative,
     * treating a negative structural level (uninitialized variables) as hard-negative too.
     *
     * @throws IllegalArgumentException for score types this PoC does not know how to decompose
     */
    public static boolean hasNegativeHardLevel(Score<?> score) {
        if (score instanceof SimpleScore simpleScore) {
            return simpleScore.structuralScore() < 0 || simpleScore.score() < 0;
        } else if (score instanceof HardSoftScore hardSoftScore) {
            return hardSoftScore.structuralScore() < 0 || hardSoftScore.hardScore() < 0;
        } else if (score instanceof HardMediumSoftScore hardMediumSoftScore) {
            return hardMediumSoftScore.structuralScore() < 0 || hardMediumSoftScore.hardScore() < 0;
        } else if (score instanceof BendableScore bendableScore) {
            if (bendableScore.structuralScore() < 0) {
                return true;
            }
            for (var level = 0; level < bendableScore.hardLevelsSize(); level++) {
                if (bendableScore.hardScore(level) < 0) {
                    return true;
                }
            }
            return false;
        } else if (score instanceof SimpleBigDecimalScore simpleScore) {
            return simpleScore.structuralScore() < 0 || simpleScore.score().signum() < 0;
        } else if (score instanceof HardSoftBigDecimalScore hardSoftScore) {
            return hardSoftScore.structuralScore() < 0 || hardSoftScore.hardScore().signum() < 0;
        } else if (score instanceof HardMediumSoftBigDecimalScore hardMediumSoftScore) {
            return hardMediumSoftScore.structuralScore() < 0 || hardMediumSoftScore.hardScore().signum() < 0;
        } else if (score instanceof BendableBigDecimalScore bendableScore) {
            if (bendableScore.structuralScore() < 0) {
                return true;
            }
            for (var level = 0; level < bendableScore.hardLevelsSize(); level++) {
                if (bendableScore.hardScore(level).signum() < 0) {
                    return true;
                }
            }
            return false;
        }
        throw new IllegalArgumentException("Unsupported score type (%s) for violation-scoped move selection."
                .formatted(score.getClass().getName()));
    }

}
