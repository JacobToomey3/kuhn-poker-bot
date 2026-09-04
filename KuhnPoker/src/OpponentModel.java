import java.util.HashMap;
import java.util.Map;

public class OpponentModel {

    // We use this to track the probability of a binary event (Bet vs Pass).
    class BetaDistribution {
        double alpha; // Pseudo-counts for Bets
        double beta;  // Pseudo-counts for Passes

        // We initialize with a "weak prior". Setting both to 2.0 creates a uniform 
        // 50/50 distribution, but prevents division by zero and ensures one weird 
        // bluff doesn't instantly skew the bot's logic 100% in one direction.
        public BetaDistribution(double priorAlpha, double priorBeta) {
            this.alpha = priorAlpha;
            this.beta = priorBeta;
        }

        // The expected value of a Beta distribution is alpha / (alpha + beta)
        public double getExpectedBetFrequency() {
            return alpha / (alpha + beta);
        }

        public double getTotalObservations() {
            return alpha + beta;
        }
    }

    // Maps the opponent's specific situation (e.g., "1p" = Jack after we pass)
    // to our statistical model of their behavior in that exact spot.
    private Map<String, BetaDistribution> opponentTendencies;

    public OpponentModel() {
        this.opponentTendencies = new HashMap<>();
    }

    // Called at showdown when we see their card and know the exact action sequence.
    public void observeAction(String infoSet, int actionTaken) {
        // If we haven't seen them in this spot, initialize the prior
        opponentTendencies.putIfAbsent(infoSet, new BetaDistribution(2.0, 2.0));

        BetaDistribution dist = opponentTendencies.get(infoSet);

        // Update the posterior distribution based on the observed data
        if (actionTaken == KuhnCFR.BET) {
            dist.alpha += 1.0;
        } else if (actionTaken == KuhnCFR.PASS) {
            dist.beta += 1.0;
        }
    }

    // Exploitative adjustments
    public double predictBetProbability(String infoSet, double gtoBetFrequency) {
        if (!opponentTendencies.containsKey(infoSet)) {
            // If we have no data on this specific node, fall back to the Nash Equilibrium
            return gtoBetFrequency;
        }

        return opponentTendencies.get(infoSet).getExpectedBetFrequency();
    }

    // Risk management (Confidence Interval)
    // A crucial feature for trading/poker algorithms. We shouldn't fully deviate 
    // from equilibrium if we only have 5 hands of data.
    public double getConfidenceWeight(String infoSet) {
        if (!opponentTendencies.containsKey(infoSet)) return 0.0;

        BetaDistribution dist = opponentTendencies.get(infoSet);
        double totalObs = dist.getTotalObservations();

        // As observations grow, confidence approaches 1.0. 
        // 4.0 is our starting prior (alpha 2 + beta 2).
        return Math.max(0.0, 1.0 - (4.0 / totalObs));
    }
}