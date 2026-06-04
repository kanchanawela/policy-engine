package com.policyengine.conflict;

import com.policyengine.model.Effect;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Most Specific Wins: among all matching statements, the one with the highest specificity score wins.
 * Ties are broken conservatively: DENY beats ALLOW.
 * No match → implicit DENY.
 */
public class MostSpecificWinsStrategy implements ConflictResolutionStrategy {

    @Override
    public ConflictResult resolve(List<StatementEvaluation> evaluations) {
        List<StatementEvaluation> matching = evaluations.stream()
                .filter(StatementEvaluation::matches)
                .toList();

        if (matching.isEmpty()) {
            return ConflictResult.implicitDeny();
        }

        int maxScore = matching.stream()
                .mapToInt(StatementEvaluation::specificityScore)
                .max()
                .getAsInt();

        List<StatementEvaluation> topScorers = matching.stream()
                .filter(e -> e.specificityScore() == maxScore)
                .toList();

        // Conservative tie-break: DENY beats ALLOW
        Optional<StatementEvaluation> denyWinner = topScorers.stream()
                .filter(e -> e.effect() == Effect.DENY)
                .findFirst();

        StatementEvaluation winner = denyWinner.orElse(topScorers.get(0));
        return ConflictResult.explicit(winner.effect(), winner.statement().original().getId());
    }

    @Override
    public String name() { return "MostSpecificWins"; }
}
